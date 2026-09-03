package com.ice.promotionservice.DTO.Request.Coupon;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ValidationCouponRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String userId;

    @NotNull
    @Positive
    private Long orderTotal;

    @NotEmpty
    private List<@Valid ValidationCouponItemRequest> items;
}
