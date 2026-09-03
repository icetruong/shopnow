package com.ice.promotionservice.DTO.Response.Coupon;

import com.ice.promotionservice.Enum.CouponDiscountType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponUserResponse {
    private String code;
    private String title;
    private String discountType;
    private Long discountValue;
    private Long maxDiscount;
    private Long minOrder;
    private Instant endsAt;
    private Boolean canUse;
}
