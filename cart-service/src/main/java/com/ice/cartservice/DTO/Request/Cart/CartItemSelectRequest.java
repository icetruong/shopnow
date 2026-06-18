package com.ice.cartservice.DTO.Request.Cart;

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
public class CartItemSelectRequest {
    @NotNull(message = "list cartItemId must not null")
    private List<String> cartItemIds;
    @NotNull(message = "selected must not null")
    private Boolean selected;
}
