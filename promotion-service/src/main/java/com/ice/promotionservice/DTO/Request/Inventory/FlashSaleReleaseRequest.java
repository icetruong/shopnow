package com.ice.promotionservice.DTO.Request.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleReleaseRequest {
    private String flashSaleId;
    private String variantId;
    private String orderId;
    private String userId;
    private Integer qty;
}
