package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Body cho /v2/switch-status/cancel và /v2/a5/gen-token: {"order_codes":[...]}. */
public record GhnOrderCodesRequest(
        @JsonProperty("order_codes") List<String> orderCodes
) {
    public static GhnOrderCodesRequest of(String code) {
        return new GhnOrderCodesRequest(List.of(code));
    }
}
