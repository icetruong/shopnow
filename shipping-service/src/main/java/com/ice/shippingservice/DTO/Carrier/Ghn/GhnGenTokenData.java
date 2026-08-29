package com.ice.shippingservice.DTO.Carrier.Ghn;

import com.fasterxml.jackson.annotation.JsonProperty;

/** data của POST /v2/a5/gen-token - token dùng dựng URL in nhãn A5. */
public record GhnGenTokenData(
        @JsonProperty("token") String token
) {
}
