package com.ice.shippingservice.DTO.Location;

import java.util.List;

/** Node trong file resources/location/vn-locations.json. */
public record DistrictSeed(
        int districtId,
        String districtName,
        List<WardSeed> wards
) {
}
