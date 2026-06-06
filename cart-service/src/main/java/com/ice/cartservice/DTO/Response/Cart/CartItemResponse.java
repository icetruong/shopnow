package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemResponse {
    private String cartItemId;
    private String variantId;
    private String productId;
    private String productName;
    private String productSlug;
    private String thumbnail;
    private String color;
    private String size;
    private String sku;
    private Long unitPrice;
    private Integer qty;
    private Long subtotal;
    private String stockStatus;
    private Integer availableQty;
    private Boolean isAvailable;
    private Instant addedAt;
}
