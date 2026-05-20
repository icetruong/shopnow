package com.ice.productservice.DTO.Response.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockItemResponse {
    private String variantId;
    private String sku;
    private Integer stockQty;
    private Integer reservedQty;
    private Integer availableQty;
    private String status;
}
