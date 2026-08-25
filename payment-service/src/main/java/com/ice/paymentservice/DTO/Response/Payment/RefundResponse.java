package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RefundResponse {
    private String refundId;
    private String paymentId;
    private Long amount;
    private String status;
    private String message;
}
