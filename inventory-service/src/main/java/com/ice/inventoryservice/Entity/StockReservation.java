package com.ice.inventoryservice.Entity;

import com.ice.inventoryservice.Enum.StockReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stock_reservations",
        indexes = {
                @Index(name = "idx_reservations_order_id", columnList = "order_id"),
                @Index(name = "idx_reservations_variant_id", columnList = "variant_id"),
                @Index(name = "idx_reservations_status", columnList = "status"),
                @Index(name = "idx_reservations_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockReservation {
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StockReservationStatus status = StockReservationStatus.RESERVED;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate()
    {
        this.updatedAt = LocalDateTime.now();
    }

    // helper
    public boolean isExpires()
    {
        return StockReservationStatus.RESERVED.equals(status)
                && LocalDateTime.now().isAfter(expiresAt);
    }
}
