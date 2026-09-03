package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationCouponResponse {
    private String code;
    private String discountType;
    private Long discountValue;
    private Long discountAmount;
    private Long maxDiscount;
    private Long finalDiscount;
    private Boolean isValid;
}
