package com.ice.inventoryservice.DTO.Response.Inventory;

import com.ice.inventoryservice.Enum.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String variantID;
    private String sku;
    private Integer stockQty;
    private Integer reservedQty;
    private Integer availableQty;
    private StockStatus status;
}
