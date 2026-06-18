package com.ice.cartservice.DTO.Request.Cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CartItemUpdateRequest {
    @NotNull(message = "quantity must not null")
    @Min(value = 1, message = "qty must >= 1")
    @Max(value = 99, message = "qty must <= 99")
    private Integer qty;
}
