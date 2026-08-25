package com.ice.paymentservice.DTO.Response.Payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Format bắt buộc theo tài liệu VNPay IPN: key phải đúng "RspCode" / "Message"
 * (không phải rspCode) để VNPay parse được, nên field JSON đặt tên qua @JsonProperty.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class VNPayIpnResponse {

    @JsonProperty("RspCode")
    private String rspCode;

    @JsonProperty("Message")
    private String message;

    public static VNPayIpnResponse of(String rspCode, String message) {
        return new VNPayIpnResponse(rspCode, message);
    }
}
