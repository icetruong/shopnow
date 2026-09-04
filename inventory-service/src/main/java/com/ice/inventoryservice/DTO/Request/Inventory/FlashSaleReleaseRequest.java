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
public class FlashSaleReleaseRequest {
    @NotBlank(message = "flashSale must not blank")
    private String flashSaleId;
    @NotBlank(message = "variantId must not blank")
    private String variantId;
    @NotBlank(message = "orderId must not blank")
    private String orderId;
    @NotBlank(message = "userId must not blank")
    private String userId;
    @NotNull(message = "quantity must not null")
    @Min(value = 1, message = "quantity must > 0")
    private Integer qty;
}
