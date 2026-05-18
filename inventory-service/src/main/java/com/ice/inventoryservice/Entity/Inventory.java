package com.ice.inventoryservice.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "inventories",
        indexes = {
                @Index(name = "idx_inventories_variant_id", columnList = "variant_id", unique = true),
                @Index(name = "idx_inventories_sku", columnList = "sku", unique = true),
                @Index(name = "idx_inventories_stock_qty", columnList = "stock_qty")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Inventory {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "variant_id", nullable = false, unique = true)
    private UUID variantId;

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "stock_qty", nullable = false)
    @Builder.Default
    private Integer stockQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    @Builder.Default
    private Integer reservedQty = 0;

    @Column(name = "sold_qty", nullable = false)
    @Builder.Default
    private Integer soldQty = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 10;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    @Transient
    public int getAvailableQty()
    {
        return stockQty - reservedQty;
    }

    public boolean isLowStock() {
        return getAvailableQty() <= lowStockThreshold;
    }

}
