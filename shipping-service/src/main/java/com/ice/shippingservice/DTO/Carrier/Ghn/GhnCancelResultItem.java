package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 1 phần tử data[] của POST /v2/switch-status/cancel. */
public record GhnCancelResultItem(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("result") boolean result,
        @JsonProperty("message") String message
) {
}
