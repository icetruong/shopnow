package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponUpdateResponse {
    private String couponId;
    private String code;
    private Integer usageLimit;
    private Integer usedCount;
    private Long remaining;
    private Instant updatedAt;
}
