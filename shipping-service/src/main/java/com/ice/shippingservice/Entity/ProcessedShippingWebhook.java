package com.ice.shippingservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "shipments",
        indexes = {
                @Index(name = "idx_shipments_order_id", columnList = "order_id", unique = true),
                @Index(name = "idx_shipments_tracking_code", columnList = "tracking_code", unique = true),
                @Index(name = "idx_shipments_status", columnList = "status"),
                @Index(name = "idx_shipments_carrier", columnList = "carrier"),
                @Index(name = "idx_shipments_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedShippingWebhook {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
    private String idempotencyKey;

    @Column(name = "carrier", nullable = false, length = 20)
    private String carrier;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();;
    }
}
