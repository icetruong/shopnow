package com.ice.inventoryservice.DTO.Response.Admin;

import com.ice.inventoryservice.Enum.StockStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockResponse {
    private String variantId;
    private String sku;
    private String productName;
    private String color;
    private String size;
    private Integer stockQty;
    private Integer reservedQty;
    private Integer availableQty;
    private Integer soldQty;
    private StockStatus status;
    private Instant updateAt;
}
