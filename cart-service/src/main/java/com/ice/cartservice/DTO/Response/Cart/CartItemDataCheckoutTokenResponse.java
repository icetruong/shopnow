package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemDataCheckoutTokenResponse {
    private String variantId;
    private String productId;
    private String productName;
    private String color;
    private String size;
    private String thumbnail;
    private String sku;
    private Long unitPrice;
    private Integer qty;
    private Long subtotal;
}
