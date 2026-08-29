package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body POST /v2/shipping-order/fee. */
public record GhnFeeRequest(
        @JsonProperty("service_id") long serviceId,
        @JsonProperty("from_district_id") int fromDistrictId,
        @JsonProperty("from_ward_code") String fromWardCode,
        @JsonProperty("to_district_id") int toDistrictId,
        @JsonProperty("to_ward_code") String toWardCode,
        @JsonProperty("weight") int weight,
        @JsonProperty("length") int length,
        @JsonProperty("width") int width,
        @JsonProperty("height") int height,
        @JsonProperty("insurance_value") long insuranceValue
) {
}
