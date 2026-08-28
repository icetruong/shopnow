package com.ice.shippingservice.DTO.Event.Consume;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderConfirmPayload {
    private String orderId;
    private String orderCode;
    private String userId;
    private ShippingAddressEvent shippingAddress;
    private List<OrderItemEvent> items;
}
