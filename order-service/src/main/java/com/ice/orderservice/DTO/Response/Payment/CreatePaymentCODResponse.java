package com.ice.orderservice.DTO.Response.Payment;

import com.ice.orderservice.Enum.PaymentGatewayStatus;
import com.ice.orderservice.Enum.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentCODResponse {
    private String paymentId;
    private PaymentMethod method;
    private PaymentGatewayStatus status;
    private String message;
}
