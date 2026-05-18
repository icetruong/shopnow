package com.ice.inventoryservice.DTO.Request.Inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemReserveRequest {
    @NotBlank(message = "variantId must not blank")
    private String variantId;
    @NotNull(message = "quantity must not null")
    @Min(value = 1, message = "quantity must greater than 0")
    private Integer qty;
}
