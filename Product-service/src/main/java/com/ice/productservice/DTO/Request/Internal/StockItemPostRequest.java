package com.ice.productservice.DTO.Request.Internal;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockItemPostRequest {
    private String variantId;
    private String sku;
    private Integer stockQty;
}
