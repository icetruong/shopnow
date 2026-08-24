package com.ice.orderservice.DTO.Response.Cart;

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
public class CartCheckoutTokenResponse {
    private String userId;
    private List<CartItemDataCheckoutTokenResponse> items;
    private String couponCode;
    private Long discountAmt;
    private Long total;
    private Instant validatedAt;
    private Instant expiresAt;
}
