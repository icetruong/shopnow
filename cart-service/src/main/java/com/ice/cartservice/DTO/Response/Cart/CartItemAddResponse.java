package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemAddResponse {
    private String cartItemId;
    private String variantId;
    private Integer qty;
    private Integer totalItems;
}
