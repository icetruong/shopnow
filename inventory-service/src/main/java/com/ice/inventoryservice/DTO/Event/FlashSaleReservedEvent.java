package com.ice.inventoryservice.DTO.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleReservedEvent {
    private String flashSaleId;
    private String variantId;
    private String orderId;
    private String userId;
    private Integer qty;
    private Instant reservedAt;
}
