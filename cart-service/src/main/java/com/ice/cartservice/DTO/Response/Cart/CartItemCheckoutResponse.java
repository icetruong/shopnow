package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemCheckoutResponse {
    private List<ItemCheckoutResponse> items;
    private CouponItemCheckoutResponse coupon;
    private PricingItemCheckoutResponse pricing;
    private String checkoutToken;
    private Instant expiresAt;
}
