package com.ice.paymentservice.DTO.Request.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MoMoQueryRequest {
    private String partnerCode;
    private String requestId;
    private String orderId;
    private String lang;
    private String signature;
}
