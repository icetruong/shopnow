package com.ice.shippingservice.DTO.Webhook;

import com.ice.shippingservice.Enum.CarrierType;
import com.ice.shippingservice.Enum.ShipmentStatus;

import java.time.Instant;

/**
 * Sự kiện webhook đã chuẩn hóa - WebhookService chỉ làm việc với cái này,
 * không quan tâm GHN hay GHTK.
 */
public record CarrierWebhookEvent(
        CarrierType carrier,
        String trackingCode,
        ShipmentStatus status,          // đã map sang vocab nội bộ
        String carrierStatusRaw,        // "delivering" / "3" - giữ gốc để lưu timeline
        String description,
        String location,                // nullable
        Instant happenedAt,
        String idempotencyKey
) {
}
