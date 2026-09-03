package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponApplyResponse {
    private String code;
    private Long remainingGlobal;
    private Long userUsageCount;
}
