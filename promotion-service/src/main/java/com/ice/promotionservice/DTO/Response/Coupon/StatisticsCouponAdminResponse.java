package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StatisticsCouponAdminResponse {
    private Long totalCoupons;
    private Long activeCoupons;
    private Long expiredCoupons;
    private Long totalRedeemed;
}
