package com.ice.cartservice.DTO.Response.Cart;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartSummaryResponse {
    private Integer totalItems;
    private Integer totalUniqueItems;
    private Long subtotal;
    private Boolean hasUnavailableItems;
}
