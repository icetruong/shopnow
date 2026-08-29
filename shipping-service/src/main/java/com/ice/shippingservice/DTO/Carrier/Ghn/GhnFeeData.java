package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** data của POST /v2/shipping-order/fee. Dùng Long (boxed) để chịu được payload thiếu field. */
public record GhnFeeData(
        @JsonProperty("total") Long total,
        @JsonProperty("service_fee") Long serviceFee,
        @JsonProperty("insurance_fee") Long insuranceFee
) {
    public long totalOrZero() {
        return total != null ? total : 0L;
    }
}
