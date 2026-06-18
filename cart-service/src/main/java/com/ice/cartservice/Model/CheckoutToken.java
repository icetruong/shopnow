package com.ice.cartservice.Model;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CheckoutToken {
    private String checkoutToken;
    private String userId;
    private List<ItemCheckoutToken> items;
    private String couponCode;
    private Long discountAmt;
    private Long shippingFee;
    private Long total;
    private String addressId;
    private Instant validatedAt;
    private Instant expiresAt;
}
