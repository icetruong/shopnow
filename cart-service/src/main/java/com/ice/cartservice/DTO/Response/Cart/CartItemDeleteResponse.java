package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemDeleteResponse {
    private String cartItemId;
    private String variantId;
    private Integer totalItems;
}
