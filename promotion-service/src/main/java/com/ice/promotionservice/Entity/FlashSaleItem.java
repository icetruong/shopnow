package com.ice.promotionservice.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "flash_sale_items",
        indexes = {
                @Index(name = "idx_flash_items_flash_sale_id", columnList = "flash_sale_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_flash_items_unique", columnNames = {"flash_sale_id", "variant_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flash_sale_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_flash_sale_items_flash_sale"))
    @OnDelete(action = OnDeleteAction.CASCADE) // khớp với "ON DELETE CASCADE" ở DB
    private FlashSale flashSale;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    /** Giá flash sale. */
    @Column(name = "flash_price", nullable = false)
    private Long flashPrice;

    /** Tổng số lượng flash. */
    @Column(name = "total_qty", nullable = false)
    private Integer totalQty;

    /** Đã bán (đồng bộ từ Redis). */
    @Builder.Default
    @Column(name = "sold_qty", nullable = false)
    private Integer soldQty = 0;

    @Builder.Default
    @Column(name = "limit_per_user", nullable = false)
    private Integer limitPerUser = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
