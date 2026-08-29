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
public class OrderCreatedPayload {
    private String orderId;
    private String orderCode;
    private String userId;
    private Long totalAmount;
    private List<OrderItemEvent> items;
    private ShippingAddressEvent shippingAddress;
}
