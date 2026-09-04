package com.ice.promotionservice.DTO.Event.Consume;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashPurchasedPayload {
    private String flashSaleId;
    private String variantId;
    private String userId;
    private String orderId;
    private Integer qty;
}
