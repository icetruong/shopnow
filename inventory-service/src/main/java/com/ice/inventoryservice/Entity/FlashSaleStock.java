package com.ice.inventoryservice.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "flash_sale_stocks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "idx_flash_sale_stocks_unique",
                        columnNames = {"flash_sale_id", "variant_id"}
                )
        },
        indexes = {
                @Index(name = "idx_flash_sale_stocks_flash_sale_id", columnList = "flash_sale_id"),
                @Index(name = "idx_flash_sale_stocks_settle", columnList = "settled_at, ends_at"),
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FlashSaleStock {
    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "flash_sale_id", nullable = false)
    private UUID flashSaleId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "initial_qty", nullable = false)
    private Integer initialQty;

    @Column(name = "sold_qty", nullable = false)
    @Builder.Default
    private Integer soldQty = 0;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Transient
    public Integer getRemainingQty()
    {
        return initialQty - soldQty;
    }

    public boolean isActive()
    {
        LocalDateTime now = LocalDateTime.now();

        return now.isAfter(startsAt) && now.isBefore(endsAt);
    }

    public boolean isSoldOut() {
        return soldQty >= initialQty;
    }
}
