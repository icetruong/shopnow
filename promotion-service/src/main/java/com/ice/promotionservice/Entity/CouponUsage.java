package com.ice.promotionservice.Entity;


import com.ice.promotionservice.Enum.CouponUsageStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "coupon_usages",
        indexes = {
                @Index(name = "idx_coupon_usages_coupon_user", columnList = "coupon_id, user_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_coupon_usages_order", columnNames = {"order_id", "coupon_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CouponUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_coupon_usages_coupon"))
    private Coupon coupon;

    @Column(name = "coupon_code", nullable = false, length = 50)
    private String couponCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    /** APPLIED / ROLLED_BACK. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponUsageStatus status = CouponUsageStatus.APPLIED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
