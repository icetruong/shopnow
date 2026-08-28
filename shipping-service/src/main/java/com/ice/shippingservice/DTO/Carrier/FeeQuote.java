package com.ice.shippingservice.DTO.Carrier;

import com.ice.shippingservice.Enum.CarrierType;

import java.time.LocalDate;

public record FeeQuote(
        CarrierType carrier,
        String serviceId,        // luôn String
        String serviceName,
        long fee,
        int estimatedDays,
        LocalDate estimatedDate
) {
}
