package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MoMoCreatePaymentResponse {
    private String partnerCode;
    private String requestId;
    private String orderId;
    private long amount;
    private String responseTime;
    private String message;
    private int resultCode;
    private String payUrl;
    private String deeplink;
    private String qrCodeUrl;
}
