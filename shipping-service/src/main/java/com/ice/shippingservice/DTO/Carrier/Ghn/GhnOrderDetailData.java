package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** data của POST /v2/shipping-order/detail. */
public record GhnOrderDetailData(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("status") String status,
        @JsonProperty("log") List<GhnLogItem> log
) {
    public GhnOrderDetailData {
        log = log == null ? List.of() : List.copyOf(log);
    }
}
