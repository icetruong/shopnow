package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body POST /v2/shipping-order/detail: {"order_code":"..."}. */
public record GhnOrderCodeRequest(
        @JsonProperty("order_code") String orderCode
) {
}
