package com.ice.orderservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmCodPaymentResponse {
    private String paymentId;
    private String status;
    private Instant paidAt;
}
