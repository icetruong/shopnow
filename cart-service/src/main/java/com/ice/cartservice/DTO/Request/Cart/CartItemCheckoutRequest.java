package com.ice.cartservice.DTO.Request.Cart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemCheckoutRequest {
    @NotNull(message = "list cartItemId must not null")
    private List<String> cartItemIds;
    private String couponCode;
    @NotBlank(message = "addressId must not blank")
    private String addressId;
}
