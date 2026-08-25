package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReconciliationMismatchDetail {
    private String orderCode;
    private String dbStatus;
    private String gatewayStatus;
    private String issue;
}
