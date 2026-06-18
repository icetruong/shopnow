package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CouponItemCheckoutResponse {
    private String code;
    private String discountType;
    private Integer discountValue;
    private Long discountAmt;
    private Boolean isValid;
}
