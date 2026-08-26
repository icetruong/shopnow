package com.ice.orderservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefundPaymentResponse {
    private String refundId;
    private String paymentId;
    private Long amount;
    private String status;
    private String message;
}
