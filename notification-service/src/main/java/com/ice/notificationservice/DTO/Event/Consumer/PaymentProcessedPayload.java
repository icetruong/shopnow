package com.ice.paymentservice.DTO.Event;

import com.ice.paymentservice.Enum.PaymentMethod;
import com.ice.paymentservice.Enum.PaymentStatus;
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
    private PaymentStatus status;
    private PaymentMethod method;
    private Long amount;
    private String transactionId;
    private Instant paidAt;
}
