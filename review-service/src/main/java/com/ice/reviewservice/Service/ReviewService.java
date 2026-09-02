package com.ice.reviewservice.Service;

import com.ice.reviewservice.Client.OrderClient;
import com.ice.reviewservice.Client.UserClient;
import com.ice.reviewservice.DTO.Event.Publish.ReviewPostedPayload;
import com.ice.reviewservice.DTO.Request.Review.CreateReviewRequest;
import com.ice.reviewservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.reviewservice.DTO.Response.Order.OrderItemDetailResponse;
import com.ice.reviewservice.DTO.Response.Review.CreateReviewResponse;
import com.ice.reviewservice.DTO.Response.User.InternalUserResponse;
import com.ice.reviewservice.Entity.ProductRatingSummary;
import com.ice.reviewservice.Entity.Review;
import com.ice.reviewservice.Entity.ReviewImage;
import com.ice.reviewservice.Enum.ReviewPostAction;
import com.ice.reviewservice.Enum.ReviewStatus;
import com.ice.reviewservice.Exception.AlreadyReviewedException;
import com.ice.reviewservice.Exception.OrderNotDeliveredException;
import com.ice.reviewservice.Exception.PurchaseRequiredException;
import com.ice.reviewservice.Repository.ProductRatingSummaryRepo;
import com.ice.reviewservice.Repository.ReviewImageRepo;
import com.ice.reviewservice.Repository.ReviewRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepo reviewRepo;
    private final OrderClient orderClient;
    private final UserClient userClient;
    private final ReviewImageRepo reviewImageRepo;
    private final ProductRatingSummaryRepo productRatingSummaryRepo;
    private final KafkaProducerService kafkaProducerService;

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

    private static final Set<String> BANNED_WORDS = Set.of("lừa đảo", "đồ rác");

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
        productRatingSummary.setAvgRating((BigDecimal.valueOf(productRatingSummary.getSumRating())
                .divide(BigDecimal.valueOf(productRatingSummary.getTotalReviews()), 2, RoundingMode.HALF_UP)));

        switch (rating)
        {
            case 5 -> productRatingSummary.setCount5(productRatingSummary.getCount5() + 1);
            case 4 -> productRatingSummary.setCount4(productRatingSummary.getCount4() + 1);
            case 3 -> productRatingSummary.setCount3(productRatingSummary.getCount3() + 1);
            case 2 -> productRatingSummary.setCount2(productRatingSummary.getCount2() + 1);
            case 1 -> productRatingSummary.setCount1(productRatingSummary.getCount1() + 1);

        }

        return productRatingSummaryRepo.save(productRatingSummary);
    }
}
