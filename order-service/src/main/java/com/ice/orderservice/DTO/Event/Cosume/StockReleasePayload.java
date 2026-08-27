package com.ice.orderservice.DTO.Event.Cosume;

import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockReleasePayload {
    private String orderId;
    private String reason;
    private Instant releasedAt;
    private List<OrderItemEvent> items;
}
