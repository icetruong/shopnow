package com.ice.orderservice.DTO.Response.Order;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminOrderResponse {
    private String orderId;
    private String orderCode;
    private String userId;
    private String status;
    private String paymentStatus;
    private Long totalAmount;
    private Integer itemCount;
    private String thumbnail;
    private String firstItemName;
    private String paymentMethod;
    private Instant createdAt;

    public static AdminOrderResponse from(Order order) {
        List<OrderItem> orderItems = order.getOrderItems();
        OrderItem firstItem = orderItems.isEmpty() ? null : orderItems.get(0);

        return new AdminOrderResponse(
                order.getId().toString(),
                order.getOrderCode(),
                order.getUserId().toString(),
                order.getStatus().name(),
                order.getPaymentStatus().name(),
                order.getTotalAmount(),
                orderItems.size(),
                firstItem == null ? null : firstItem.getThumbnail(),
                firstItem == null ? null : firstItem.getProductName(),
                order.getPaymentMethod().name(),
                order.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
