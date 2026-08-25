package com.ice.paymentservice.DTO.Request.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MoMoRefundRequest {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private long transId;
    private String lang;
    private String description;
    private String signature;
}
