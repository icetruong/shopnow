package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Body POST /v2/shipping-order/available-services. */
public record GhnAvailableServicesRequest(
        @JsonProperty("shop_id") int shopId,
        @JsonProperty("from_district") int fromDistrict,
        @JsonProperty("to_district") int toDistrict
) {
}
