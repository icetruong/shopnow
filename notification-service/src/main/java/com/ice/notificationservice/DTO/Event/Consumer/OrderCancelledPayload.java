package com.ice.notificationservice.DTO.Event.Consumer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderCancelledPayload {
    private String orderId;
    private String reason;
    private Boolean needReleaseStock;
    private List<OrderItemEvent> items;
}
