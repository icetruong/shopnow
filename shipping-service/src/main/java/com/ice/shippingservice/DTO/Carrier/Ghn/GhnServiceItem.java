package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 1 phần tử trong data[] của available-services. */
public record GhnServiceItem(
        @JsonProperty("service_id") long serviceId,
        @JsonProperty("short_name") String shortName,
        @JsonProperty("service_type_id") Integer serviceTypeId
) {
}
