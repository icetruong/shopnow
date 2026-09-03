package com.ice.promotionservice.DTO.Request.Coupon;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponRollbackRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String userId;

    @NotBlank
    private String orderId;
}
