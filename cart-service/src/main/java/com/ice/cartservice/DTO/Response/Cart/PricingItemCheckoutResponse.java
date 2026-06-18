package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PricingItemCheckoutResponse {
    private Long subtotal;
    private Long discount;
    private Long shippingFee;
    private Long total;
}
