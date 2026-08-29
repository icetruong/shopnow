package com.ice.shippingservice.DTO.Carrier.Ghtk;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /services/shipment/fee -> {"success":..,"message":..,"fee":{...}}. */
public record GhtkFeeResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message,
        @JsonProperty("fee") Fee fee
) {
    public record Fee(
            @JsonProperty("name") String name,
            @JsonProperty("fee") long fee,
            @JsonProperty("insurance_fee") long insuranceFee
    ) {
    }
}
