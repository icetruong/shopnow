package com.ice.inventoryservice.DTO.Request.Admin;

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
public class ItemFlashSaleRequest {
    @NotBlank(message = "variantId at must not blank")
    private String variantId;
    @NotNull(message = "flash sale quantity must not null")
    @Min(value = 1, message = "flash sale quantity must > 0")
    private Integer flashSaleQty;
}
