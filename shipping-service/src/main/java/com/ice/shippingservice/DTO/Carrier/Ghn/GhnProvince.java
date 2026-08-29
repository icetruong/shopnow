package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** GET /master-data/province - data[]. VERIFY: GHN master-data dùng PascalCase. */
public record GhnProvince(
        @JsonProperty("ProvinceID") int provinceId,
        @JsonProperty("ProvinceName") String provinceName
) {
}
