package com.ice.orderservice.DTO.Response.Order;

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
    /** userId chủ đơn — service nội bộ (Notification, Shipping) cần để biết gửi cho ai. */
    private String userId;
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
