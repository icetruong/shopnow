package com.ice.reviewservice.Service;

import com.ice.reviewservice.Client.OrderClient;
import com.ice.reviewservice.Client.UserClient;
import com.ice.reviewservice.DTO.Event.Publish.ReviewPostedPayload;
import com.ice.reviewservice.DTO.Request.Review.*;
import com.ice.reviewservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.reviewservice.DTO.Response.Order.OrderItemDetailResponse;
import com.ice.reviewservice.DTO.Response.Order.OrderTimelineResponse;
import com.ice.reviewservice.DTO.Response.Review.*;
import com.ice.reviewservice.DTO.Response.User.InternalUserResponse;
import com.ice.reviewservice.Entity.*;
import com.ice.reviewservice.Enum.ReviewPostAction;
import com.ice.reviewservice.Enum.ReviewStatus;
import com.ice.reviewservice.Exception.AlreadyReportedException;
import com.ice.reviewservice.Exception.AlreadyReviewedException;
import com.ice.reviewservice.Exception.EditWindowExpiredException;
import com.ice.reviewservice.Exception.OrderNotDeliveredException;
import com.ice.reviewservice.Exception.PurchaseRequiredException;
import com.ice.reviewservice.Exception.ReviewNotFoundException;
import com.ice.reviewservice.Repository.*;
import com.ice.reviewservice.Util.ReviewSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private static final int EDIT_WINDOW_DAYS = 7;
    /** Số report tối thiểu để tự chuyển review sang REPORTED cho admin xem lại. */
    private static final int REPORT_THRESHOLD = 5;

    private final ReviewRepo reviewRepo;
    private final OrderClient orderClient;
    private final UserClient userClient;
    private final ReviewImageRepo reviewImageRepo;
    private final ProductRatingSummaryRepo productRatingSummaryRepo;
    private final KafkaProducerService kafkaProducerService;
    private final ReviewReplyRepo reviewReplyRepo;
    private final ReviewHelpfulRepo reviewHelpfulRepo;
    private final ReviewReportRepo reviewReportRepo;

    @Transactional
    public CreateReviewResponse createReview(String userId, CreateReviewRequest request) {

        OrderDetailResponse orderDetailResponse = orderClient.getOrder(request.getOrderId());

        if(!orderDetailResponse.getUserId().equals(userId))
            throw new PurchaseRequiredException("Bạn cần mua sản phẩm này trước khi đánh giá.");

        if(!orderDetailResponse.getStatus().equals("DELIVERED") && !orderDetailResponse.getStatus().equals("COMPLETED"))
            throw new OrderNotDeliveredException("đơn hàng chưa được giao nên không review được");


        OrderItemDetailResponse match = null;
        for(OrderItemDetailResponse orderItemDetailResponse : orderDetailResponse.getItems())
        {
            if(orderItemDetailResponse.getVariantId().equals(request.getVariantId()))
            {
                match = orderItemDetailResponse;
                break;
            }
        }

        if (match == null)
            throw new PurchaseRequiredException("Bạn cần mua sản phẩm này trước khi đánh giá.");

        if(reviewRepo.existsByUserIdAndOrderIdAndVariantId(UUID.fromString(userId), UUID.fromString(request.getOrderId()), UUID.fromString(request.getVariantId())))
            throw new AlreadyReviewedException("Đã review rồi");

        String variantInfo = Stream.of(match.getColor(), match.getSize())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" - "));

        ModerationResult mod = moderate(request.getComment());

        InternalUserResponse user = userClient.getUser(userId);
        String userName = (user.getFullName() == null || user.getFullName().isBlank())
                ? "Người dùng ẩn danh"          // fallback vì cột NOT NULL
                : user.getFullName();

        Review review = Review.builder()
                .userId(UUID.fromString(userId))
                .userName(userName)
                .productId(UUID.fromString(request.getProductId()))
                .variantId(UUID.fromString(request.getVariantId()))
                .orderId(UUID.fromString(request.getOrderId()))
                .variantInfo(variantInfo.isBlank() ? null : variantInfo)
                .rating(request.getRating().shortValue())
                .comment(request.getComment())
                .status(mod.status())
                .flaggedReason(mod.flaggedReason())
                .isVerifiedPurchase(true)
                .build();

        reviewRepo.save(review);

        List<String> imgUrls = request.getImages();
        if (imgUrls != null && !imgUrls.isEmpty()) {
            List<ReviewImage> images = new ArrayList<>();
            for (int i = 0; i < imgUrls.size(); i++) {
                images.add(ReviewImage.builder()
                        .review(review)
                        .url(imgUrls.get(i))
                        .sortOrder(i)
                        .build());
            }
            reviewImageRepo.saveAll(images);
        }

        if(review.getStatus() == ReviewStatus.APPROVED)
        {
            ProductRatingSummary productRatingSummary = applyRatingToSummary(UUID.fromString(request.getProductId()), request.getRating());

            kafkaProducerService.publishReviewPostedEvent(new ReviewPostedPayload(
                    review.getId().toString(),
                    review.getProductId().toString(),
                    review.getUserId().toString(),
                    request.getRating().doubleValue(),
                    productRatingSummary.getAvgRating().doubleValue(),
                    productRatingSummary.getTotalReviews().longValue(),
                    ReviewPostAction.CREATED
            ));
        }

        return new CreateReviewResponse(
                review.getId().toString(),
                review.getStatus().toString(),
                review.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }

    public ReviewPageResponse getReview(int page, int size, Short rating, Boolean hasImage, String sort, String productId) {

        UUID productUuid = UUID.fromString(productId);

        Specification<Review> specification = Specification.where(ReviewSpecification.hasRating(rating))
                .and(ReviewSpecification.hasProductId(productUuid))
                .and(ReviewSpecification.hasImage(hasImage))
                .and(ReviewSpecification.hasStatus(ReviewStatus.APPROVED));

        Page<Review> reviews = reviewRepo.findAll(specification, PageRequest.of(page, size, resolveSort(sort)));
        List<Review> content = reviews.getContent();
        List<UUID> reviewIds = content.stream().map(Review::getId).toList();

        // gom 1 query cho ảnh + 1 query cho shop reply, thay vì 2 query mỗi review (N+1)
        Map<UUID, List<String>> imagesByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewImageRepo.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getReview().getId(),
                        Collectors.mapping(
                                ReviewImage::getUrl, Collectors.toList()
                        )
                ));

        Map<UUID, ReviewReply> replyByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewReplyRepo.findByReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(
                        reviewReply -> reviewReply.getReview().getId(),
                        reviewReply -> reviewReply
                ));

        List<ReviewResponse> reviewResponses = content.stream()
                .map(review -> {
                    ShopReplyResponse shopReply = Optional.ofNullable(replyByReview.get(review.getId()))
                            .map(reply -> new ShopReplyResponse(
                                    reply.getContent(),
                                    reply.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()))
                            .orElse(null);

                    return new ReviewResponse(
                            review.getId().toString(),
                            maskName(review.getUserName()),
                            review.getUserAvatar(),
                            review.getRating().intValue(),
                            review.getComment(),
                            imagesByReview.getOrDefault(review.getId(), List.of()),
                            review.getVariantInfo(),
                            review.getIsVerifiedPurchase(),
                            review.getHelpfulCount(),
                            shopReply,
                            review.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                    );
                }).toList();

        ProductRatingSummary productRatingSummary = productRatingSummaryRepo
                .findById(productUuid)
                .orElseGet(() -> ProductRatingSummary.builder()
                        .productId(productUuid)
                        .build());

        Map<String, Integer> distribution = new LinkedHashMap<>();
        distribution.put("5", productRatingSummary.getCount5());
        distribution.put("4", productRatingSummary.getCount4());
        distribution.put("3", productRatingSummary.getCount3());
        distribution.put("2", productRatingSummary.getCount2());
        distribution.put("1", productRatingSummary.getCount1());

        return new ReviewPageResponse(
                reviewResponses,
                page,
                reviews.getTotalElements(),
                new ReviewSummaryResponse(
                        productRatingSummary.getAvgRating().doubleValue(),
                        productRatingSummary.getTotalReviews().longValue(),
                        distribution,
                        reviewRepo.countReviewsWithImage(productUuid, ReviewStatus.APPROVED)
                )
        );
    }

    @Transactional(readOnly = true)
    public PageReviewMeResponse getMeReview(int page, int size, ReviewStatus status, String sort, String userId) {
        Specification<Review> specification = Specification.where(ReviewSpecification.hasStatus(status))
                .and(ReviewSpecification.hasUserId(UUID.fromString(userId)));

        Page<Review> reviews = reviewRepo.findAll(specification, PageRequest.of(page, size, resolveSort(sort)));

        List<Review> content = reviews.getContent();
        List<UUID> reviewIds = content.stream().map(Review::getId).toList();

        Map<UUID, ReviewReply> replyByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewReplyRepo.findByReviewIdIn(reviewIds)
                .stream().collect(Collectors.toMap(
                        reviewReply -> reviewReply.getReview().getId(),
                        reviewReply -> reviewReply
                ));

        Map<UUID, List<String>> imagesByReview = reviewIds.isEmpty()
                ? Map.of()
                : reviewImageRepo.findByReviewIdIn(reviewIds)
                .stream().collect(Collectors.groupingBy(
                        reviewImage -> reviewImage.getReview().getId(),
                        Collectors.mapping(
                                ReviewImage::getUrl,
                                Collectors.toList()
                        )
                ));

        List<ReviewMeResponse> reviewMeResponses = content.stream()
                .map(review -> {
                    ShopReplyResponse shopReplyResponse = Optional.ofNullable(replyByReview.get(review.getId()))
                            .map(reviewReply -> new ShopReplyResponse(reviewReply.getContent(), reviewReply.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()))
                            .orElse(null);

                    return new ReviewMeResponse(
                            review.getId().toString(),
                            review.getProductId().toString(),
                            review.getVariantId().toString(),
                            review.getVariantInfo(),
                            review.getRating(),
                            review.getComment(),
                            imagesByReview.getOrDefault(review.getId(), List.of()),
                            review.getStatus().name(),
                            review.getFlaggedReason(),
                            review.getHelpfulCount(),
                            shopReplyResponse,
                            review.getCreatedAt().isAfter(LocalDateTime.now().minusDays(EDIT_WINDOW_DAYS)),
                            review.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(),
                            review.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant()
                    );
                }).toList();

        return new PageReviewMeResponse(
                reviewMeResponses,
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    public List<ReviewPendingResponse> getPendingReview(String userId) {
        List<OrderDetailResponse> orderDetailResponses = orderClient.getOrderOfUser(userId);

        List<Review> reviews = reviewRepo.findAllByUserId(UUID.fromString(userId));

        Set<String> reviewed = reviews.stream().map(
                review -> review.getOrderId() + "|" + review.getVariantId()
        ).collect(Collectors.toSet());

        List<ReviewPendingResponse> reviewPendingResponses = new ArrayList<>();

        for (OrderDetailResponse orderDetailResponse : orderDetailResponses)
        {
            Instant deliverAt = extractDeliverAt(orderDetailResponse);

            for(OrderItemDetailResponse orderItemDetailResponse : orderDetailResponse.getItems())
            {
                String key = orderDetailResponse.getOrderId() + "|" + orderItemDetailResponse.getVariantId();

                if (reviewed.contains(key))
                    continue;

                String variantInfo = Stream.of(orderItemDetailResponse.getColor(), orderItemDetailResponse.getSize())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" - "));

                reviewPendingResponses.add(new ReviewPendingResponse(
                        orderDetailResponse.getOrderId(),
                        orderItemDetailResponse.getProductId(),
                        orderItemDetailResponse.getVariantId(),
                        orderItemDetailResponse.getProductName(),
                        orderItemDetailResponse.getThumbnail(),
                        variantInfo,
                        deliverAt
                ));
            }
        }

        return reviewPendingResponses;
    }

    private Instant extractDeliverAt(OrderDetailResponse orderDetailResponse)
    {
        if (orderDetailResponse.getTimeline() == null)
            return null;

        return orderDetailResponse.getTimeline().stream()
                .filter(orderTimelineResponse -> orderTimelineResponse.getStatus().equals("DELIVERED"))
                .map(OrderTimelineResponse::getAt)
                .findFirst()
                .orElseGet(() -> orderDetailResponse.getTimeline().stream()
                        .filter(orderTimelineResponse -> orderTimelineResponse.getStatus().equals("COMPLETED"))
                        .map(OrderTimelineResponse::getAt)
                        .findFirst()
                        .orElse(null)
                );
    }

    @Transactional
    public void updateReview(String reviewId, UpdateReviewRequest request, String userId) {

        Review review = reviewRepo.findByIdAndUserId(UUID.fromString(reviewId), UUID.fromString(userId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review of user"));

        if (review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(EDIT_WINDOW_DAYS)))
            throw new EditWindowExpiredException("Quá " + EDIT_WINDOW_DAYS + " ngày, không sửa được đánh giá.");
        Short oldRating = review.getRating();
        review.setRating(request.getRating().shortValue());
        review.setComment(request.getComment());

        reviewRepo.save(review);

        if(review.getStatus() == ReviewStatus.APPROVED)
        {
            ProductRatingSummary productRatingSummary = productRatingSummaryRepo.findByIdForUpdate(review.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Không tìm thấy rating summary cho product " + review.getProductId()
                                    + " dù review đang APPROVED"));

            productRatingSummary.setSumRating(productRatingSummary.getSumRating() - oldRating + review.getRating());
            adjustCount(productRatingSummary, review.getRating(), +1);
            adjustCount(productRatingSummary, oldRating, -1);
            productRatingSummary.setAvgRating(BigDecimal.valueOf(productRatingSummary.getSumRating())
                    .divide(BigDecimal.valueOf(productRatingSummary.getTotalReviews()), 2, RoundingMode.HALF_UP));
            productRatingSummaryRepo.save(productRatingSummary);

            kafkaProducerService.publishReviewPostedEvent(new ReviewPostedPayload(
                    review.getId().toString(),
                    review.getProductId().toString(),
                    review.getUserId().toString(),
                    request.getRating().doubleValue(),
                    productRatingSummary.getAvgRating().doubleValue(),
                    productRatingSummary.getTotalReviews().longValue(),
                    ReviewPostAction.UPDATED
            ));
        }

    }


    private static final Set<String> BANNED_WORDS = Set.of("lừa đảo", "đồ rác");

    @Transactional
    public void deleteReview(String reviewId, String userId) {
        Review review = reviewRepo.findByIdAndUserId(UUID.fromString(reviewId), UUID.fromString(userId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review of user"));

        if(review.getStatus() == ReviewStatus.APPROVED)
        {
            ProductRatingSummary productRatingSummary = productRatingSummaryRepo.findByIdForUpdate(review.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Không tìm thấy rating summary cho product " + review.getProductId()
                                    + " dù review đang APPROVED"));

            productRatingSummary.setTotalReviews(productRatingSummary.getTotalReviews() - 1);
            productRatingSummary.setSumRating(productRatingSummary.getSumRating() - review.getRating());
            adjustCount(productRatingSummary, review.getRating(), -1);
            productRatingSummary.setAvgRating(
                    productRatingSummary.getTotalReviews() == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(productRatingSummary.getSumRating())
                                    .divide(BigDecimal.valueOf(productRatingSummary.getTotalReviews()), 2, RoundingMode.HALF_UP)
            );
            productRatingSummaryRepo.save(productRatingSummary);

            kafkaProducerService.publishReviewPostedEvent(new ReviewPostedPayload(
                    review.getId().toString(),
                    review.getProductId().toString(),
                    review.getUserId().toString(),
                    review.getRating().doubleValue(),
                    productRatingSummary.getAvgRating().doubleValue(),
                    productRatingSummary.getTotalReviews().longValue(),
                    ReviewPostAction.DELETED
            ));
        }

        reviewRepo.delete(review);
    }

    @Transactional
    public HelpfulReviewResponse helpfulReview(String reviewId, String userId) {
        Review review = reviewRepo.findById(UUID.fromString(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review"));

        ReviewHelpful reviewHelpful = reviewHelpfulRepo.findByReviewIdAndUserId(UUID.fromString(reviewId), UUID.fromString(userId))
                        .orElseGet(() -> ReviewHelpful.builder()
                                .review(review)
                                .userId(UUID.fromString(userId))
                                .build());

        if(reviewHelpful.getId() == null)
        {
            review.setHelpfulCount(review.getHelpfulCount()+1);
            reviewHelpfulRepo.save(reviewHelpful);
            reviewRepo.save(review);

            return new HelpfulReviewResponse(
                    review.getHelpfulCount(),
                    true
            );
        }
        else
        {
            review.setHelpfulCount(review.getHelpfulCount()-1);
            reviewHelpfulRepo.delete(reviewHelpful);
            reviewRepo.save(review);

            return new HelpfulReviewResponse(
                    review.getHelpfulCount(),
                    false
            );
        }
    }

    public void replyReview(ReplyReviewRequest request, String reviewId, String userId) {
        Review review = reviewRepo.findById(UUID.fromString(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review"));

        ReviewReply reviewReply = reviewReplyRepo.findByReviewId(UUID.fromString(reviewId))
                        .orElseGet(() -> ReviewReply.builder()
                                .review(review)
                                .build());

        reviewReply.setContent(request.getContent());
        reviewReply.setRepliedBy(UUID.fromString(userId));
        reviewReplyRepo.save(reviewReply);
    }

    @Transactional(readOnly = true)
    public PageReviewAdminResponse getReviewForAdmin(int page,int size, ReviewStatus status) {
        // Không truyền status -> hàng chờ kiểm duyệt = PENDING + REPORTED
        Specification<Review> specification = (status == null)
                ? Specification.where(ReviewSpecification.hasStatusIn(List.of(ReviewStatus.PENDING, ReviewStatus.REPORTED)))
                : Specification.where(ReviewSpecification.hasStatus(status));

        Page<Review> reviews = reviewRepo.findAll(specification, PageRequest.of(page, size, resolveSort("newest")));

        List<ReviewAdminResponse> reviewAdminResponses = reviews.getContent().stream()
                .map(review -> new ReviewAdminResponse(
                        review.getId().toString(),
                        review.getProductId().toString(),
                        review.getUserName(),
                        review.getRating(),
                        review.getComment(),
                        review.getFlaggedReason(),
                        review.getReportCount(),
                        review.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                )).toList();

        return new PageReviewAdminResponse(
                reviewAdminResponses,
                reviews.getNumber(),
                reviews.getSize(),
                reviews.getTotalElements(),
                reviews.getTotalPages()
        );
    }

    @Transactional
    public void approve(String reviewId) {
        Review review = reviewRepo.findById(UUID.fromString(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review"));

        if(review.getStatus() == ReviewStatus.APPROVED)
            throw new IllegalArgumentException("review này đã được approved");

        review.setStatus(ReviewStatus.APPROVED);
        review.setFlaggedReason(null);
        reviewRepo.save(review);

        ProductRatingSummary productRatingSummary = applyRatingToSummary(review.getProductId(), review.getRating());

        kafkaProducerService.publishReviewPostedEvent(new ReviewPostedPayload(
                review.getId().toString(),
                review.getProductId().toString(),
                review.getUserId().toString(),
                review.getRating().doubleValue(),
                productRatingSummary.getAvgRating().doubleValue(),
                productRatingSummary.getTotalReviews().longValue(),
                ReviewPostAction.CREATED
        ));
    }

    @Transactional
    public void reject(String reviewId, RejectReviewRequest request) {
        Review review = reviewRepo.findById(UUID.fromString(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review"));

        if(review.getStatus() == ReviewStatus.REJECTED)
            throw new IllegalArgumentException("review này đã được reject");
        ReviewStatus oldStatus = review.getStatus();
        review.setStatus(ReviewStatus.REJECTED);
        review.setFlaggedReason(request.getReason());

        reviewRepo.save(review);

        if(oldStatus == ReviewStatus.APPROVED || oldStatus == ReviewStatus.REPORTED)
        {
            ProductRatingSummary productRatingSummary = productRatingSummaryRepo.findByIdForUpdate(review.getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Không tìm thấy rating summary cho product " + review.getProductId()
                                    + " dù review đang APPROVED"));

            productRatingSummary.setTotalReviews(productRatingSummary.getTotalReviews() - 1);
            productRatingSummary.setSumRating(productRatingSummary.getSumRating() - review.getRating());
            adjustCount(productRatingSummary, review.getRating(), -1);
            productRatingSummary.setAvgRating(
                    productRatingSummary.getTotalReviews() == 0
                            ? BigDecimal.ZERO
                            : BigDecimal.valueOf(productRatingSummary.getSumRating())
                            .divide(BigDecimal.valueOf(productRatingSummary.getTotalReviews()), 2, RoundingMode.HALF_UP)
            );
            productRatingSummaryRepo.save(productRatingSummary);

            kafkaProducerService.publishReviewPostedEvent(new ReviewPostedPayload(
                    review.getId().toString(),
                    review.getProductId().toString(),
                    review.getUserId().toString(),
                    review.getRating().doubleValue(),
                    productRatingSummary.getAvgRating().doubleValue(),
                    productRatingSummary.getTotalReviews().longValue(),
                    ReviewPostAction.DELETED
            ));
        }
    }

    @Transactional
    public void report(String reviewId, ReportReviewRequest request, String userId) {
        Review review = reviewRepo.findById(UUID.fromString(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException("not found review"));

        if (reviewReportRepo.existsByReviewIdAndUserId(UUID.fromString(reviewId), UUID.fromString(userId)))
            throw new AlreadyReportedException("Bạn đã báo cáo đánh giá này rồi.");

        ReviewReport reviewReport = ReviewReport.builder()
                .review(review)
                .userId(UUID.fromString(userId))
                .reason(request.getReason())
                .build();

        review.setReportCount(review.getReportCount() + 1);
        // Không kéo review đã bị admin ẩn (REJECTED) ngược lại hàng chờ
        if (review.getReportCount() >= REPORT_THRESHOLD && review.getStatus() != ReviewStatus.REJECTED)
            review.setStatus(ReviewStatus.REPORTED);

        reviewRepo.save(review);

        reviewReportRepo.save(reviewReport);
    }

    record ModerationResult(ReviewStatus status, String flaggedReason) {}

    private ModerationResult moderate(String comment) {
        if (comment == null || comment.isBlank())
            return new ModerationResult(ReviewStatus.APPROVED, null);

        String lower = comment.toLowerCase();
        boolean hasBanned = BANNED_WORDS.stream().anyMatch(lower::contains);
        boolean hasLink    = lower.matches(".*(http://|https://|www\\.).*");

        if (hasBanned) return new ModerationResult(ReviewStatus.PENDING, "AUTO_PROFANITY");
        if (hasLink)   return new ModerationResult(ReviewStatus.PENDING, "AUTO_LINK");
        return new ModerationResult(ReviewStatus.APPROVED, null);
    }

    private ProductRatingSummary applyRatingToSummary(UUID productId, int rating)
    {
        ProductRatingSummary productRatingSummary = productRatingSummaryRepo.findByIdForUpdate(productId)
                .orElseGet(() -> ProductRatingSummary.builder().productId(productId).build());

        productRatingSummary.setTotalReviews(productRatingSummary.getTotalReviews() + 1);
        productRatingSummary.setSumRating(productRatingSummary.getSumRating() + rating);
        productRatingSummary.setAvgRating(BigDecimal.valueOf(productRatingSummary.getSumRating())
                .divide(BigDecimal.valueOf(productRatingSummary.getTotalReviews()), 2, RoundingMode.HALF_UP));

        adjustCount(productRatingSummary, rating, +1);

        return productRatingSummaryRepo.save(productRatingSummary);
    }

    /** Tăng/giảm bộ đếm count5..count1 tương ứng với số sao. delta = +1 khi thêm, -1 khi bớt. */
    private void adjustCount(ProductRatingSummary summary, int star, int delta)
    {
        switch (star)
        {
            case 5 -> summary.setCount5(summary.getCount5() + delta);
            case 4 -> summary.setCount4(summary.getCount4() + delta);
            case 3 -> summary.setCount3(summary.getCount3() + delta);
            case 2 -> summary.setCount2(summary.getCount2() + delta);
            case 1 -> summary.setCount1(summary.getCount1() + delta);
        }
    }

    private Sort resolveSort(String sort) {
        return switch (sort) {
            case "helpful"     -> Sort.by(Sort.Direction.DESC, "helpfulCount");
            case "rating_high" -> Sort.by(Sort.Direction.DESC, "rating");
            case "rating_low"  -> Sort.by(Sort.Direction.ASC,  "rating");
            case "oldest"      -> Sort.by(Sort.Direction.ASC, "createdAt");
            default            -> Sort.by(Sort.Direction.DESC, "createdAt"); // newest
        };
    }

    /** "Nguyễn Văn A" -> "Nguyễn V. A" : giữ nguyên tên đầu, các từ sau chỉ còn chữ cái đầu. */
    private String maskName(String fullName) {
        if (fullName == null || fullName.isBlank())
            return "Người dùng ẩn danh";

        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0];

        StringBuilder masked = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            masked.append(' ')
                    .append(Character.toUpperCase(parts[i].charAt(0)))
                    .append('.');
        }
        return masked.toString();
    }
}
