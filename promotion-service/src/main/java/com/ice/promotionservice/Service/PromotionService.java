package com.ice.promotionservice.Service;

import com.ice.promotionservice.Client.InventoryClient;
import com.ice.promotionservice.Client.ProductClient;
import com.ice.promotionservice.DTO.Request.Coupon.*;
import com.ice.promotionservice.DTO.Request.FlashSale.FlashSalePurchaseRequest;
import com.ice.promotionservice.DTO.Request.FlashSale.FlashSaleRollbackRequest;
import com.ice.promotionservice.DTO.Request.Inventory.FlashSaleReleaseRequest;
import com.ice.promotionservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.promotionservice.DTO.Request.Product.ProductBatchRequest;
import com.ice.promotionservice.DTO.Response.Coupon.AdminCreateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponAdminResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponApplyResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUpdateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.PageCouponAdminResponse;
import com.ice.promotionservice.DTO.Response.Coupon.StatisticsCouponAdminResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUserResponse;
import com.ice.promotionservice.DTO.Response.Coupon.ValidationCouponResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.FlashSaleActiveItemResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.FlashSaleActiveResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.FlashSalePurchaseResponse;
import com.ice.promotionservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.promotionservice.DTO.Response.Product.ProductBatchResponse;
import com.ice.promotionservice.DTO.Response.Product.ProductItemBatchResponse;
import com.ice.promotionservice.Entity.*;
import com.ice.promotionservice.Enum.*;
import com.ice.promotionservice.Exception.CouponAdminException;
import com.ice.promotionservice.Exception.CouponInvalidException;
import com.ice.promotionservice.Exception.FlashSaleException;
import com.ice.promotionservice.Exception.InventoryServiceUnavailableException;
import com.ice.promotionservice.Exception.ResourceNotFoundException;
import com.ice.promotionservice.Repository.*;
import com.ice.promotionservice.Util.CouponSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CouponRepo couponRepo;
    private final CouponUsageRepo couponUsageRepo;
    private final CouponCounterService couponCounterService;
    private final FlashSaleRepo flashSaleRepo;
    private final FlashSaleItemRepo flashSaleItemRepo;
    private final FlashSalePurchaseRepo flashSalePurchaseRepo;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    public ValidationCouponResponse validationCoupon(ValidationCouponRequest request) {

        String code = request.getCode().trim().toUpperCase();
        Coupon coupon = couponRepo.findByCode(code)
                .filter(Coupon::getIsActive)
                .orElseThrow(() -> new CouponInvalidException(CouponInvalidReason.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if(now.isAfter(coupon.getEndsAt()))
            throw new CouponInvalidException(CouponInvalidReason.EXPIRED);

        if(now.isBefore(coupon.getStartsAt()))
            throw new CouponInvalidException(CouponInvalidReason.NOT_STARTED);

        Long remaining = couponCounterService.getUsageRemaining(coupon.getCode());
        boolean soldOut = (remaining != null)
                ? remaining <= 0
                : coupon.getUsedCount() >= coupon.getUsageLimit();

        if (soldOut)
            throw new CouponInvalidException(CouponInvalidReason.USAGE_LIMIT_REACHED);

        Long usedByUser = couponCounterService.getUserHadUsed(code, request.getUserId());
        int usedUser = usedByUser == null
                ? couponUsageRepo.countByUserIdAndCouponIdAndStatus(UUID.fromString(request.getUserId()), coupon.getId(), CouponUsageStatus.APPLIED)
                : usedByUser.intValue();
        if(usedUser >= coupon.getUserLimit())
            throw new CouponInvalidException(CouponInvalidReason.USER_LIMIT_REACHED);

        if(request.getOrderTotal() < coupon.getMinOrder())
            throw new CouponInvalidException(CouponInvalidReason.MIN_ORDER_NOT_MET);

        if(!isApplicable(coupon, request.getItems()))
            throw new CouponInvalidException(CouponInvalidReason.NOT_APPLICABLE);

        long discountAmount = calculateDiscount(coupon, request.getOrderTotal());
        long finalDiscount = discountAmount;

        if(coupon.getDiscountType() == CouponDiscountType.PERCENTAGE && coupon.getMaxDiscount() != null)
            finalDiscount = Math.min(discountAmount, coupon.getMaxDiscount());

        return new ValidationCouponResponse(
                coupon.getCode(),
                coupon.getDiscountType().name(),
                coupon.getDiscountValue(),
                discountAmount,
                coupon.getMaxDiscount(),
                finalDiscount,
                true
        );
    }

    private boolean isApplicable(Coupon coupon, List<ValidationCouponItemRequest> items)
    {
        if(coupon.getApplicableType() == CouponApplicableType.ALL)
            return true;

        List<UUID> allowedIds = coupon.getApplicableIds();
        if (items == null || items.isEmpty() || allowedIds == null || allowedIds.isEmpty())
            return false;

        for(ValidationCouponItemRequest item : items)
        {
            String rawId = (coupon.getApplicableType() == CouponApplicableType.PRODUCT)
                    ? item.getProductId()
                    : item.getCategoryId();

            if(rawId != null && allowedIds.contains(UUID.fromString(rawId)))
                return true;
        }

        return false;
    }

    private Long calculateDiscount(Coupon coupon, Long orderTotal)
    {
        return switch (coupon.getDiscountType())
        {
            case PERCENTAGE -> orderTotal * coupon.getDiscountValue() / 100 ;
            case FIXED_AMOUNT -> Math.min(coupon.getDiscountValue(), orderTotal);
            case FREESHIP -> 0L;
        };
    }

    public List<CouponUserResponse> getCouponForUser(String userId) {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponRepo.findAllByIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(now, now);

        return coupons.stream()
                .map(coupon -> new CouponUserResponse(
                        coupon.getCode(),
                        coupon.getTitle(),
                        coupon.getDiscountType().name(),
                        coupon.getDiscountValue(),
                        coupon.getMaxDiscount(),
                        coupon.getMinOrder(),
                        coupon.getEndsAt().atZone(ZoneId.systemDefault()).toInstant(),
                        canUse(coupon, userId)
                )).toList();
    }

    @Transactional
    public CouponApplyResponse apply(CouponApplyRequest request) {
        String code = request.getCode().trim().toUpperCase();

        var existing = couponUsageRepo.findByCouponCodeAndOrderId(code, UUID.fromString(request.getOrderId()));
        if(existing.isPresent())
            return new CouponApplyResponse(
                    code,
                    couponCounterService.getUsageRemaining(code),
                    couponCounterService.getUserHadUsed(code, request.getUserId())
            );
        Coupon coupon = couponRepo.findByCode(code)
                .filter(Coupon::getIsActive)
                .orElseThrow(() -> new CouponInvalidException(CouponInvalidReason.NOT_FOUND));

        Long remaining = couponCounterService.decrementUsage(code);
        if(remaining<0)
        {
            couponCounterService.incrementUsage(code);
            throw new CouponInvalidException(CouponInvalidReason.USAGE_LIMIT_REACHED);
        }

        Long userCount = couponCounterService.incrementUser(code, request.getUserId(), coupon.getEndsAt());
        coupon.setUsedCount(coupon.getUsageLimit() - remaining.intValue());
        couponRepo.save(coupon);

        CouponUsage couponUsage = CouponUsage.builder()
                .coupon(coupon)
                .couponCode(code)
                .userId(UUID.fromString(request.getUserId()))
                .orderId(UUID.fromString(request.getOrderId()))
                .status(CouponUsageStatus.APPLIED)
                .build();
        couponUsageRepo.save(couponUsage);

        return new CouponApplyResponse(
                code,
                remaining,
                userCount
        );
    }

    @Transactional
    public void rollback(CouponRollbackRequest request) {
        String code = request.getCode().trim().toUpperCase();

        CouponUsage couponUsage = couponUsageRepo
                .findByCouponCodeAndOrderId(code, UUID.fromString(request.getOrderId()))
                .orElse(null);

        // Không tìm thấy bản ghi, hoặc đã rollback rồi -> không có gì để hoàn, coi như thành công
        if (couponUsage == null || couponUsage.getStatus() != CouponUsageStatus.APPLIED)
            return;

        Coupon coupon = couponRepo.findByCode(code)
                .orElseThrow(() -> new CouponInvalidException(CouponInvalidReason.NOT_FOUND));

        Long remaining = couponCounterService.incrementUsage(code);
        Long userCount = couponCounterService.decrementUser(code, request.getUserId());
        coupon.setUsedCount(coupon.getUsageLimit() - remaining.intValue());
        couponRepo.save(coupon);

        couponUsage.setStatus(CouponUsageStatus.ROLLED_BACK);
        couponUsageRepo.save(couponUsage);
    }

    private boolean canUse(Coupon coupon, String userId)
    {
        Long remaining = couponCounterService.getUsageRemaining(coupon.getCode());
        boolean hasGlobalQuota = (remaining != null)
                ? remaining > 0
                : coupon.getUsedCount() < coupon.getUsageLimit();
        if (!hasGlobalQuota)
            return false;

        Long userUsed = couponCounterService.getUserHadUsed(coupon.getCode(), userId);
        int used = (userUsed != null)
                ? userUsed.intValue()
                : couponUsageRepo.countByUserIdAndCouponIdAndStatus(
                UUID.fromString(userId), coupon.getId(), CouponUsageStatus.APPLIED);
        return used < coupon.getUserLimit();
    }

    public AdminCreateResponse createCoupon(AdminCreateRequest request) {
        String code = request.getCode().trim().toUpperCase();

        // 1. Trùng code (code là UNIQUE)
        if (couponRepo.findByCode(code).isPresent())
            throw new CouponAdminException(CouponAdminError.CODE_DUPLICATED);

        // 2. Khoảng thời gian hợp lệ
        LocalDateTime startsAt = request.getStartsAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endsAt = request.getEndsAt().atZone(ZoneId.systemDefault()).toLocalDateTime();
        if (!endsAt.isAfter(startsAt) || !endsAt.isAfter(LocalDateTime.now()))
            throw new CouponAdminException(CouponAdminError.TIME_RANGE_INVALID);

        // 3. discountValue theo loại giảm giá
        if (request.getDiscountType() == CouponDiscountType.PERCENTAGE
                && (request.getDiscountValue() < 1 || request.getDiscountValue() > 100))
            throw new CouponAdminException(CouponAdminError.DISCOUNT_VALUE_INVALID);

        // 4. applicableIds bắt buộc khi giới hạn theo CATEGORY/PRODUCT
        if (request.getApplicableType() != CouponApplicableType.ALL
                && (request.getApplicableIds() == null || request.getApplicableIds().isEmpty()))
            throw new CouponAdminException(CouponAdminError.APPLICABLE_IDS_REQUIRED);

        // 5. Lưu DB trước
        Coupon coupon = Coupon.builder()
                .code(code)
                .title(request.getTitle())
                .discountType(request.getDiscountType())
                .discountValue(request.getDiscountValue())
                .maxDiscount(request.getMaxDiscount())
                .minOrder(request.getMinOrder())
                .usageLimit(request.getUsageLimit())
                .userLimit(request.getUserLimit())
                .applicableType(request.getApplicableType())
                .applicableIds(request.getApplicableIds())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build();
        couponRepo.save(coupon);

        // 6. Seed Redis counter SAU khi DB ok
        couponCounterService.createUsageRemaining(coupon.getUsageLimit().longValue(), code, endsAt);

        return new AdminCreateResponse(
                coupon.getId().toString(),
                coupon.getCode()
        );
    }

    @Transactional
    public CouponUpdateResponse updateCoupon(UUID couponId, CouponUpdateRequest request) {
        // 1. Tìm coupon
        Coupon coupon = couponRepo.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy coupon: " + couponId));

        // 2. code không được đổi
        if (request.getCode() != null
                && !request.getCode().trim().toUpperCase().equals(coupon.getCode()))
            throw new CouponAdminException(CouponAdminError.CODE_IMMUTABLE);

        // 3. Gộp giá trị mới với giá trị cũ (field nào null thì giữ nguyên)
        CouponDiscountType discountType = request.getDiscountType() != null
                ? request.getDiscountType() : coupon.getDiscountType();
        Long discountValue = request.getDiscountValue() != null
                ? request.getDiscountValue() : coupon.getDiscountValue();
        CouponApplicableType applicableType = request.getApplicableType() != null
                ? request.getApplicableType() : coupon.getApplicableType();
        List<UUID> applicableIds = request.getApplicableIds() != null
                ? request.getApplicableIds() : coupon.getApplicableIds();
        LocalDateTime startsAt = request.getStartsAt() != null
                ? request.getStartsAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : coupon.getStartsAt();
        LocalDateTime endsAt = request.getEndsAt() != null
                ? request.getEndsAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : coupon.getEndsAt();

        // 4. Validate nghiệp vụ trên giá trị đã gộp
        if (!endsAt.isAfter(startsAt))
            throw new CouponAdminException(CouponAdminError.TIME_RANGE_INVALID);

        if (discountType == CouponDiscountType.PERCENTAGE
                && (discountValue < 1 || discountValue > 100))
            throw new CouponAdminException(CouponAdminError.DISCOUNT_VALUE_INVALID);

        if (applicableType != CouponApplicableType.ALL
                && (applicableIds == null || applicableIds.isEmpty()))
            throw new CouponAdminException(CouponAdminError.APPLICABLE_IDS_REQUIRED);

        // 5. Đổi usageLimit → chỉnh counter Redis theo đúng phần chênh lệch
        Integer newUsageLimit = request.getUsageLimit();
        if (newUsageLimit != null && !newUsageLimit.equals(coupon.getUsageLimit())) {
            if (newUsageLimit < coupon.getUsedCount())
                throw new CouponAdminException(CouponAdminError.USAGE_LIMIT_BELOW_USED);
            long delta = newUsageLimit - coupon.getUsageLimit();
            couponCounterService.adjustUsageRemaining(coupon.getCode(), delta);
            coupon.setUsageLimit(newUsageLimit);
        }

        // 6. Đổi endsAt → cập nhật TTL của counter Redis
        if (request.getEndsAt() != null && !endsAt.equals(coupon.getEndsAt()))
            couponCounterService.updateUsageTtl(coupon.getCode(), endsAt);

        // 7. Ghi các field còn lại
        if (request.getTitle() != null) coupon.setTitle(request.getTitle());
        if (request.getMaxDiscount() != null) coupon.setMaxDiscount(request.getMaxDiscount());
        if (request.getMinOrder() != null) coupon.setMinOrder(request.getMinOrder());
        if (request.getUserLimit() != null) coupon.setUserLimit(request.getUserLimit());
        if (request.getIsActive() != null) coupon.setIsActive(request.getIsActive());
        coupon.setDiscountType(discountType);
        coupon.setDiscountValue(discountValue);
        coupon.setApplicableType(applicableType);
        coupon.setApplicableIds(applicableIds);
        coupon.setStartsAt(startsAt);
        coupon.setEndsAt(endsAt);

        couponRepo.save(coupon);

        // 8. Trả về (remaining đọc lại từ Redis)
        return new CouponUpdateResponse(
                coupon.getId().toString(),
                coupon.getCode(),
                coupon.getUsageLimit(),
                coupon.getUsedCount(),
                couponCounterService.getUsageRemaining(coupon.getCode()),
                Instant.now()
        );
    }

    public void deleteCoupon(UUID couponId) {
        Coupon coupon = couponRepo.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy coupon: " + couponId));

        if(Boolean.FALSE.equals(coupon.getIsActive()))
            throw new CouponAdminException(CouponAdminError.COUPON_ALREADY_INACTIVE);

        coupon.setIsActive(false);
        couponRepo.save(coupon);

        couponCounterService.deleteUsage(coupon.getCode());
    }

    @Transactional(readOnly = true)
    public PageCouponAdminResponse listCoupons(int page, int size, String status, String keyword) {
        // 1. Chuẩn hoá tham số phân trang
        int safePage = Math.max(page, 0);
        int safeSize = (size < 1 || size > MAX_PAGE_SIZE) ? DEFAULT_PAGE_SIZE : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by("createdAt").descending());

        LocalDateTime now = LocalDateTime.now();

        // 2. "ALL"/null -> không lọc; giá trị lạ -> IllegalArgumentException -> 400 INVALID_REQUEST
        CouponStatus statusFilter = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? null
                : CouponStatus.valueOf(status.trim().toUpperCase());

        // 3. Lấy 1 trang coupon từ DB theo filter
        Page<Coupon> result = couponRepo.findAll(CouponSpecification.filter(statusFilter, keyword, now), pageable);

        // 4. remaining: gom code cả trang -> MGET 1 lần
        List<String> codes = result.getContent().stream().map(Coupon::getCode).toList();
        Map<String, Long> remainingByCode = couponCounterService.getUsageRemainingBatch(codes);

        List<CouponAdminResponse> content = result.getContent().stream()
                .map(c -> toCouponAdminResponse(c, now, remainingByCode.get(c.getCode())))
                .toList();

        // 5. Trả về theo format phân trang chuẩn + block statistics
        return new PageCouponAdminResponse(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                (long) result.getTotalPages(),
                buildStatistics(now)
        );
    }

    public FlashSaleActiveResponse getFlashSaleActive() {
        FlashSale flashSale = flashSaleRepo.findActive(LocalDateTime.now()).stream()
                .findFirst()
                .orElse(null);

        if (flashSale == null) {
            return null; // controller vẫn trả success=true, data=null
        }

        List<FlashSaleItem> flashSaleItems = flashSaleItemRepo.findAllByFlashSaleId(flashSale.getId());

        List<FlashSaleActiveItemResponse> flashSaleActiveItemResponses =
                buildActiveItems(flashSaleItems);

        return new FlashSaleActiveResponse(
                flashSale.getId().toString(),
                flashSale.getTitle(),
                flashSale.getStartsAt().atZone(ZoneId.systemDefault()).toInstant(),
                flashSale.getEndsAt().atZone(ZoneId.systemDefault()).toInstant(),
                Instant.now(),
                flashSaleActiveItemResponses
        );
    }

    public FlashSalePurchaseResponse purchase(FlashSalePurchaseRequest request) {
        FlashSale flashSale = flashSaleRepo.findById(UUID.fromString(request.getFlashSaleId()))
                .orElseThrow(() -> new FlashSaleException(FlashSaleError.FLASH_SALE_NOT_FOUND));

        FlashSaleItem flashSaleItem = flashSaleItemRepo.findByIdAndFlashSaleIdAndVariantId(
                        UUID.fromString(request.getFlashItemId()),
                        UUID.fromString(request.getFlashSaleId()),
                        UUID.fromString(request.getVariantId()))
                .orElseThrow(() -> new FlashSaleException(FlashSaleError.FLASH_SALE_ITEM_NOT_FOUND));

        FlashSaleReserveResponse reserveResponse = inventoryClient.reserve(new FlashSaleReserveRequest(
                flashSale.getId().toString(),
                request.getVariantId(),
                request.getOrderId(),
                request.getUserId(),
                request.getQty(),
                flashSaleItem.getLimitPerUser()
        ));

        if (reserveResponse == null || reserveResponse.getRemaining() == null) {
            throw new InventoryServiceUnavailableException("inventory-service reserve trả body rỗng");
        }

        return new FlashSalePurchaseResponse(
                flashSaleItem.getFlashPrice(),
                reserveResponse.getRemaining(),
                reserveResponse.getReservedAt()
        );
    }

    @Transactional
    public void rollbackFlashSale(FlashSaleRollbackRequest request) {

        FlashSalePurchase flashSalePurchase = flashSalePurchaseRepo.findByFlashSaleIdAndOrderId(
                        UUID.fromString(request.getFlashSaleId()),
                        UUID.fromString(request.getOrderId()))
                .orElse(null);

        // Idempotent (spec bước 3): không có bản ghi purchase, hoặc đã ROLLED_BACK -> bỏ qua, vẫn trả 200.
        if (flashSalePurchase == null
                || flashSalePurchase.getStatus() == FlashSalePurchaseStatus.ROLLED_BACK) {
            return;
        }

        // Tra item TRƯỚC khi gọi release: nếu item không còn, fail sớm để không release Redis rồi mới rollback DB.
        FlashSaleItem flashSaleItem = flashSaleItemRepo.findByFlashSaleIdAndVariantId(
                        UUID.fromString(request.getFlashSaleId()),
                        UUID.fromString(request.getVariantId()))
                .orElseThrow(() -> new FlashSaleException(FlashSaleError.FLASH_SALE_ITEM_NOT_FOUND));

        inventoryClient.release(new FlashSaleReleaseRequest(
                request.getFlashSaleId(),
                request.getVariantId(),
                request.getOrderId(),
                request.getUserId(),
                flashSalePurchase.getQty()
        ));

        flashSalePurchase.setStatus(FlashSalePurchaseStatus.ROLLED_BACK);
        flashSaleItem.setSoldQty(Math.max(0, flashSaleItem.getSoldQty() - flashSalePurchase.getQty()));

        flashSalePurchaseRepo.save(flashSalePurchase);
        flashSaleItemRepo.save(flashSaleItem);
    }

    private List<FlashSaleActiveItemResponse> buildActiveItems(List<FlashSaleItem> flashSaleItems) {
        // Flash sale không có item -> trả list rỗng, KHÔNG gọi product-service với variantIds rỗng (sẽ 400).
        if (flashSaleItems.isEmpty()) {
            return List.of();
        }

        List<String> variantIds = flashSaleItems.stream()
                .map(flashSaleItem -> flashSaleItem.getVariantId().toString())
                .toList();

        ProductBatchResponse productBatchResponse =
                productClient.getBatch(new ProductBatchRequest(variantIds));

        // getBatch() throw ProductServiceUnavailableException khi service lỗi -> tới đây body đã 200.
        // Vẫn phòng body 200 nhưng rỗng/thiếu field.
        List<ProductItemBatchResponse> variants =
                (productBatchResponse == null || productBatchResponse.getVariants() == null)
                        ? List.of()
                        : productBatchResponse.getVariants();

        Map<UUID, ProductItemBatchResponse> productByVariantId = variants.stream()
                .collect(Collectors.toMap(
                        variant -> UUID.fromString(variant.getVariantId()),
                        variant -> variant
                ));

        List<FlashSaleActiveItemResponse> result = new ArrayList<>();

        for (FlashSaleItem flashSaleItem : flashSaleItems) {
            ProductItemBatchResponse product = productByVariantId.get(flashSaleItem.getVariantId());
            // Spec product-service: variantId không tồn tại bị bỏ khỏi mảng "variants".
            // -> bỏ qua item này, không render card lỗi trên trang chủ.
            if (product == null) {
                continue;
            }

            int totalQty = flashSaleItem.getTotalQty() == null ? 0 : flashSaleItem.getTotalQty();
            int soldRaw = flashSaleItem.getSoldQty() == null ? 0 : flashSaleItem.getSoldQty();
            // sold_qty đồng bộ async từ Kafka, có thể tạm vượt total -> kẹp về [0, totalQty].
            int soldQty = Math.min(Math.max(soldRaw, 0), totalQty);
            int remaining = Math.max(0, totalQty - soldQty);
            int soldPercent = totalQty == 0 ? 0 : Math.min(100, soldQty * 100 / totalQty);

            Long originalPrice = product.getPrice();
            Long discountPct = (originalPrice == null || originalPrice <= 0)
                    ? null
                    : (originalPrice - flashSaleItem.getFlashPrice()) * 100 / originalPrice;

            result.add(new FlashSaleActiveItemResponse(
                    flashSaleItem.getId().toString(),
                    flashSaleItem.getProductId().toString(),
                    flashSaleItem.getVariantId().toString(),
                    product.getProductName(),
                    product.getThumbnail(),
                    originalPrice,
                    flashSaleItem.getFlashPrice(),
                    discountPct,
                    totalQty,
                    soldQty,
                    remaining,
                    soldPercent,
                    flashSaleItem.getLimitPerUser()
            ));
        }

        return result;
    }

    private StatisticsCouponAdminResponse buildStatistics(LocalDateTime now) {
        return new StatisticsCouponAdminResponse(
                couponRepo.count(),
                couponRepo.countByIsActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(now, now),
                couponRepo.countByIsActiveTrueAndEndsAtLessThan(now),
                couponUsageRepo.countByStatus(CouponUsageStatus.APPLIED)
        );
    }

    private CouponAdminResponse toCouponAdminResponse(Coupon c, LocalDateTime now, Long redisRemaining) {
        long remaining = (redisRemaining != null)
                ? redisRemaining
                : (long) c.getUsageLimit() - c.getUsedCount();   // fallback khi Redis chưa có key

        return new CouponAdminResponse(
                c.getId().toString(),
                c.getCode(),
                c.getTitle(),
                c.getDiscountType().name(),
                c.getDiscountValue(),
                c.getMaxDiscount(),
                c.getMinOrder(),
                c.getUsageLimit().longValue(),
                c.getUsedCount().longValue(),
                remaining,
                c.getUserLimit().longValue(),
                c.getApplicableType().name(),
                c.getStartsAt().atZone(ZoneId.systemDefault()).toInstant(),
                c.getEndsAt().atZone(ZoneId.systemDefault()).toInstant(),
                c.getIsActive(),
                deriveStatus(c, now).name(),
                c.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
        );
    }

    /** status suy ra, theo thứ tự ưu tiên: INACTIVE > SCHEDULED > EXPIRED > ACTIVE. */
    private CouponStatus deriveStatus(Coupon c, LocalDateTime now) {
        if (Boolean.FALSE.equals(c.getIsActive())) return CouponStatus.INACTIVE;
        if (now.isBefore(c.getStartsAt())) return CouponStatus.SCHEDULED;
        if (now.isAfter(c.getEndsAt())) return CouponStatus.EXPIRED;
        return CouponStatus.ACTIVE;
    }
}
