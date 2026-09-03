package com.ice.orderservice.DTO.Event.Publish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload event {@code order.refunded} — phát khi Order Service nhận {@code payment.refunded}
 * và đã chuyển order sang REFUNDED. Notification Service consume để gửi email "Đã hoàn tiền".
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class OrderRefundedPayload {
    private String orderId;
    private String orderCode;
    private String userId;
    private Long amount;      // số tiền đã hoàn (từ payment.refunded)
    private String refundId;  // để đối soát với Payment Service
}
