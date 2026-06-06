package com.ice.cartservice.Model;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem {
    private String cartItemId;
    private String variantId;
    private String productId;
    private String sku;
    private Integer qty;
    private Boolean selected;
    private Instant addedAt;
}
