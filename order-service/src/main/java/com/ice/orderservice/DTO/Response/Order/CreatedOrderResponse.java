package com.ice.orderservice.DTO.Response.Order;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreatedOrderResponse {
    private String orderId;
    private String orderCode;
    private String status;
    private Long totalAmount;
    private String paymentMethod;
    private String paymentUrl;
    private Instant createdAt;
}
