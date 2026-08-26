package com.ice.orderservice.DTO.Request.Payment;

import com.ice.orderservice.Enum.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CreatePaymentRequest {
    private String orderId;
    private String orderCode;
    private String userId;
    private Long amount;
    private PaymentMethod method;
    private String returnUrl;
    private String bankCode;
}
