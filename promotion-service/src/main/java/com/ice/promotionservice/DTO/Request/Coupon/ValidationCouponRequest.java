package com.ice.promotionservice.DTO.Response.Coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationCouponResponse {
    private String code;
    private String userId;
    private Integer orderTotal;
    private List<ValidationCouponItemResponse> items;
}
