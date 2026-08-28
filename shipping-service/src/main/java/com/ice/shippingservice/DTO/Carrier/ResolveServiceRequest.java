package com.ice.shippingservice.DTO.Carrier;

public record ResolveServiceRequest(
        int fromDistrictId,
        int toDistrictId,
        int weightGram
) {
}
