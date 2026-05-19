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
public class InsertInventoryRequest {
    @NotBlank(message = "variantId must not blank")
    private String variantId;
    @NotBlank(message = "sku must not blank")
    private String sku;
    @NotNull(message = "stockQty must not null")
    @Min(value = 1, message = "stockQty must > 0")
    private Integer stockQty;
}
