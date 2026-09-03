package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationCouponItemResponse {
    private String productId;
    private String categoryId;
    private Integer qty;
    private Long price;
}
