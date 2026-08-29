package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /master-data/ward?district_id= - data[]. */
public record GhnWard(
        @JsonProperty("WardCode") String wardCode,
        @JsonProperty("WardName") String wardName,
        @JsonProperty("DistrictID") int districtId
) {
}
