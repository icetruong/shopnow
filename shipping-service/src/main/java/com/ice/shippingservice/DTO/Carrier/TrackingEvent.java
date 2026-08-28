package com.ice.shippingservice.DTO.Carrier;

import com.ice.shippingservice.Enum.ShipmentStatus;

import java.time.Instant;

public record TrackingEvent(
        ShipmentStatus status,        // đã map về status nội bộ
        String carrierStatus,         // giữ nguyên gốc ("delivering" / "3")
        String description,
        String location,
        Instant happenedAt
) {
}
