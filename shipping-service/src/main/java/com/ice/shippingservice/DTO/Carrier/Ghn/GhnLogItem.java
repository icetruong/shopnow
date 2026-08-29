package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 1 dòng trong data.log[] của /v2/shipping-order/detail. */
public record GhnLogItem(
        @JsonProperty("status") String status,
        @JsonProperty("updated_date") String updatedDate,   // ISO datetime
        @JsonProperty("location_name") String locationName
) {
}
