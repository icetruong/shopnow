package com.ice.promotionservice.Entity;

import com.ice.promotionservice.Enum.FlashSalePurchaseStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "flash_sale_purchases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "idx_flash_purchases_unique",
                        columnNames = {"flash_sale_id", "user_id", "order_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashSalePurchase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "flash_sale_id", nullable = false)
    private UUID flashSaleId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Column(name = "flash_price", nullable = false)
    private Long flashPrice;

    /** PURCHASED / ROLLED_BACK. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FlashSalePurchaseStatus status = FlashSalePurchaseStatus.PURCHASED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
