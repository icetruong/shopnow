package com.ice.paymentservice.DTO.Response.Payment;

import com.ice.paymentservice.Enum.PaymentMethod;
import com.ice.paymentservice.Enum.PaymentStatus;
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
    private PaymentStatus status;
    private String message;
}
