package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ItemCheckoutResponse {
    private String cartItemId;
    private String variantId;
    private String productName;
    private String color;
    private String size;
    private Long unitPrice;
    private Integer qty;
    private Long subtotal;
    private Boolean isAvailable;
}
