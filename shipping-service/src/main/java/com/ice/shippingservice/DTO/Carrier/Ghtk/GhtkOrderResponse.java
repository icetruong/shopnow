package com.ice.shippingservice.DTO.Carrier.Ghtk;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Vỏ response create/tracking GHTK: {"success":..,"message":..,"order":{...}}. */
public record GhtkOrderResponse(
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message,
        @JsonProperty("order") GhtkOrderData order
) {
}
