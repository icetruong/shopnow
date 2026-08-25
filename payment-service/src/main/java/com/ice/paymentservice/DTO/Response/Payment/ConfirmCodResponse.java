package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmCodResponse {
    private String paymentId;
    private String status;
    private Instant paidAt;
}
