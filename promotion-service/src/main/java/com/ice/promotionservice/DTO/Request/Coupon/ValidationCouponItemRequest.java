package com.ice.promotionservice.DTO.Request.Coupon;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationCouponItemRequest {

    @NotBlank
    private String productId;

    private String categoryId;

    @NotNull
    @Positive
    private Integer qty;

    @NotNull
    @Positive
    private Long price;
}
