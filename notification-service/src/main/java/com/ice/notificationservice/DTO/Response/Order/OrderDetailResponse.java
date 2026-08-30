package com.ice.notificationservice.DTO.Response.Order;

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
public class OrderDetailResponse {
    private String orderId;
    private String orderCode;
    private String status;
    private List<OrderItemDetailResponse> items;
    private OrderShippingAddressResponse shippingAddress;
    private OrderPricingResponse pricing;
    private String coupon;
    private String paymentMethod;
    private String paymentStatus;
    private String note;
    private List<OrderTimelineResponse> timeline;
    private Instant createdAt;
}
