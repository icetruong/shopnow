package com.ice.shippingservice.DTO.Webhook;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Body GHN gửi tới POST /shipping/webhook/ghn (field đã camelCase). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GhnWebhookRequest {
    private String orderCode;      // = trackingCode bên mình
    private String status;         // "delivering" / "delivered" / ...
    private String description;
    private String warehouse;      // vị trí
    private Instant time;          // "2026-08-28T09:00:00Z" - ISO, Jackson tự parse
}
