package com.ice.shippingservice.DTO.Carrier.Ghtk;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response tối thiểu của GHTK: {"success":true,"message":"..."}. */
public record GhtkAck(
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message
) {
}
