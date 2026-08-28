package com.ice.shippingservice.DTO.Location;

import java.util.List;

/** Node trong file resources/location/vn-locations.json. */
public record ProvinceSeed(
        int provinceId,
        String provinceName,
        List<DistrictSeed> districts
) {
}
