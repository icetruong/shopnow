package com.ice.promotionservice.Exception;

import com.ice.promotionservice.Enum.CouponInvalidReason;
import lombok.Getter;

@Getter
public class CouponInvalidException extends RuntimeException {
    private final CouponInvalidReason couponInvalidReason;
    public CouponInvalidException(CouponInvalidReason couponInvalidReason) {
        super(couponInvalidReason.getMessage());
        this.couponInvalidReason = couponInvalidReason;
    }
}
