package com.ice.cartservice.DTO.Response.Inventory;

import com.ice.cartservice.Enum.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockResponse {
    private String variantId;
    private String sku;
    private Integer stockQty;
    private Integer reservedQty;
    private Integer availableQty;
    private StockStatus status;
}
