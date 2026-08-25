package com.ice.paymentservice.DTO.Response.Payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MoMoQueryResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private String transId;
    private int resultCode;
    private String message;
}
