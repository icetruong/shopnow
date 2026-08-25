package com.ice.paymentservice.DTO.Response.Payment;

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
public class PaymentDetailResponse {
    private String paymentId;
    private String orderId;
    private String orderCode;
    private String userId;
    private PaymentMethod method;
    private Long amount;
    private PaymentStatus status;
    private String transactionId;
    private Instant paidAt;
    private Instant createdAt;
}
