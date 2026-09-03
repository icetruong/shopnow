package com.ice.promotionservice.Service;

import com.ice.promotionservice.DTO.Request.Coupon.*;
import com.ice.promotionservice.DTO.Response.Coupon.AdminCreateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponApplyResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUpdateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUserResponse;
import com.ice.promotionservice.DTO.Response.Coupon.ValidationCouponResponse;
import com.ice.promotionservice.Entity.Coupon;
import com.ice.promotionservice.Entity.CouponUsage;
import com.ice.promotionservice.Enum.CouponApplicableType;
import com.ice.promotionservice.Enum.CouponDiscountType;
import com.ice.promotionservice.Enum.CouponAdminError;
import com.ice.promotionservice.Enum.CouponInvalidReason;
import com.ice.promotionservice.Enum.CouponUsageStatus;
import com.ice.promotionservice.Exception.CouponAdminException;
import com.ice.promotionservice.Exception.CouponInvalidException;
import com.ice.promotionservice.Exception.ResourceNotFoundException;
import com.ice.promotionservice.Repository.CouponRepo;
import com.ice.promotionservice.Repository.CouponUsageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final CouponRepo couponRepo;
    private final CouponUsageRepo couponUsageRepo;
    private final CouponCounterService couponCounterService;

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
}
