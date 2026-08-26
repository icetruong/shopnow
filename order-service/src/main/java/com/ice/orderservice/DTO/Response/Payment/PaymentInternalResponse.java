package com.ice.orderservice.DTO.Response.Payment;

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
public class PaymentInternalResponse {
    private String paymentId;
    private String orderId;
    private PaymentMethod method;
    private Long amount;
    private PaymentGatewayStatus status;
    private Instant paidAt;
}
