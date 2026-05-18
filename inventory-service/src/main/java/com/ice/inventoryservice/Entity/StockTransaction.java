package com.ice.inventoryservice.Entity;

import com.ice.inventoryservice.Enum.StockTransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stock_transactions",
        indexes = {
                @Index(name = "idx_stock_txn_variant_id", columnList = "variant_id"),
                @Index(name = "idx_stock_txn_order_id", columnList = "order_id"),
                @Index(name = "idx_stock_txn_type", columnList = "type"),
                @Index(name = "idx_stock_txn_created_at", columnList = "created_at DESC")
        }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockTransaction {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private StockTransactionType type;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "qty_before", nullable = false)
    private Integer qtyBefore;

    @Column(name = "qty_after", nullable = false)
    private Integer qtyAfter;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
