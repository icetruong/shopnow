package com.ice.orderservice.DTO.Event.Cosume;

import com.ice.orderservice.Enum.PaymentGatewayStatus;
import com.ice.orderservice.Enum.PaymentMethod;
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
    private PaymentGatewayStatus status;
    private PaymentMethod method;
    private Long amount;
    private String transactionId;
    private Instant paidAt;
}
