package com.ice.promotionservice.Service;

import com.ice.promotionservice.DTO.Request.Coupon.ValidationCouponItemRequest;
import com.ice.promotionservice.DTO.Request.Coupon.ValidationCouponRequest;
import com.ice.promotionservice.DTO.Response.Coupon.ValidationCouponResponse;
import com.ice.promotionservice.Entity.Coupon;
import com.ice.promotionservice.Enum.CouponApplicableType;
import com.ice.promotionservice.Enum.CouponDiscountType;
import com.ice.promotionservice.Enum.CouponInvalidReason;
import com.ice.promotionservice.Enum.CouponUsageStatus;
import com.ice.promotionservice.Exception.CouponInvalidException;
import com.ice.promotionservice.Repository.CouponRepo;
import com.ice.promotionservice.Repository.CouponUsageRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final CouponRepo couponRepo;
    private final CouponUsageRepo couponUsageRepo;
    private final CouponUsageCounter couponUsageCounter;

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

        Long remaining = couponUsageCounter.getRemaining(coupon.getCode());
        boolean soldOut = (remaining != null)
                ? remaining <= 0
                : coupon.getUsedCount() >= coupon.getUsageLimit();

        if (soldOut)
            throw new CouponInvalidException(CouponInvalidReason.USAGE_LIMIT_REACHED);

        int usedByUser = couponUsageRepo.countByUserIdAndCouponIdAndStatus(UUID.fromString(request.getUserId()), coupon.getId(), CouponUsageStatus.APPLIED);

        if(usedByUser >= coupon.getUserLimit())
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
}
