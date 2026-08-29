package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /master-data/district?province_id= - data[]. */
public record GhnDistrict(
        @JsonProperty("DistrictID") int districtId,
        @JsonProperty("DistrictName") String districtName,
        @JsonProperty("ProvinceID") int provinceId
) {
}
