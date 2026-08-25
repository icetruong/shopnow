package com.ice.paymentservice.DTO.Response.Payment;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VNPayReturnResponse {
    private boolean validSignature;
    private String paymentId;
    private String vnpResponseCode;
    private boolean success;
}
