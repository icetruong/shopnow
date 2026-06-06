package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ListCartItemResponse {
    private String cartId;
    private String userId;
    private List<CartItemResponse> items;
}
