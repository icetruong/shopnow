package com.ice.orderservice.DTO.Event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderPaidPayload {
    private String orderId;
    private String orderCode;
    private List<OrderItemEvent> items;
}
