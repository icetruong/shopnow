package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** data của POST /v2/shipping-order/create. */
public record GhnCreateOrderData(
        @JsonProperty("order_code") String orderCode,
        @JsonProperty("total_fee") Long totalFee,
        /** ISO datetime, vd "2024-01-17T00:00:00.000Z" - parse lấy phần ngày. */
        @JsonProperty("expected_delivery_time") String expectedDeliveryTime
) {
}
