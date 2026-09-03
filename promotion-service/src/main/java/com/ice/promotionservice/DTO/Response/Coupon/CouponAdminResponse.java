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
public class CouponAdminResponse {
    private String couponId;
    private String code;
    private String title;
    private String discountType;
    private Long discountValue;
    private Long maxDiscount;
    private Long minOrder;
    private Long usageLimit;
    private Long usedCount;
    private Long remaining;
    private Long userLimit;
    private String applicableType;
    private Instant startsAt;
    private Instant endsAt;
    private Boolean isActive;
    private String status;
    private Instant createdAt;
}
