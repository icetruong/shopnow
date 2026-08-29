package com.ice.notificationservice.DTO.Event.Consumer;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentProcessedPayload {
    private String orderId;
    private String paymentId;
    private String status;
    private String method;
    private Long amount;
    private String transactionId;
    private Instant paidAt;
}
