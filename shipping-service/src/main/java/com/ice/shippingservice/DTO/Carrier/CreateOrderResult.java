package com.ice.shippingservice.DTO.Carrier;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.time.LocalDate;

public record CreateOrderResult(
        String trackingCode,
        ShipmentStatus status,        // thường READY_TO_PICK
        LocalDate estimatedDate,
        String labelUrl
) {
}
