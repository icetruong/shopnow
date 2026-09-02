package com.ice.promotionservice.DTO.Event.Publish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FlashPurchasedPayload {
    private String flashSaleId;
    private String variantId;
    private String userId;
    private String orderId;
    private Integer qty;
    private Long flashPrice;
}
