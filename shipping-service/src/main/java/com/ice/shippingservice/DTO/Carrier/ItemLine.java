package com.ice.shippingservice.DTO.Carrier;

public record ItemLine(
        String name,
        int quantity,
        int weightGram
) {
}
