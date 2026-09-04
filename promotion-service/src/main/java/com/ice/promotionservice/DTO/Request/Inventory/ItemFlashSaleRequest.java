package com.ice.promotionservice.DTO.Request.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemFlashSaleRequest {
    private String variantId;
    private Integer flashSaleQty;
}
