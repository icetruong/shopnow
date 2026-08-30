package com.ice.promotionservice.Entity;

import com.ice.promotionservice.Enum.CouponApplicableType;
import com.ice.promotionservice.Enum.CouponDiscountType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "coupons",
        indexes = {
                @Index(name = "idx_coupons_code", columnList = "code", unique = true),
                @Index(name = "idx_coupons_active_time", columnList = "is_active, starts_at, ends_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Mã coupon (uppercase). */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** PERCENTAGE / FIXED_AMOUNT / FREESHIP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private CouponDiscountType discountType;

    /** 10 (%) hoặc 50000 (VND). */
    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    /** Giảm tối đa (cho %). */
    @Column(name = "max_discount")
    private Long maxDiscount;

    /** Giá trị đơn tối thiểu. */
    @Builder.Default
    @Column(name = "min_order", nullable = false)
    private Long minOrder = 0L;

    /** Tổng lượt dùng. */
    @Column(name = "usage_limit", nullable = false)
    private Integer usageLimit;

    /** Đã dùng (đồng bộ từ Redis). */
    @Builder.Default
    @Column(name = "used_count", nullable = false)
    private Integer usedCount = 0;

    /** Mỗi user tối đa mấy lần. */
    @Builder.Default
    @Column(name = "user_limit", nullable = false)
    private Integer userLimit = 1;

    /** ALL / CATEGORY / PRODUCT. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_type", nullable = false, length = 20)
    private CouponApplicableType applicableType = CouponApplicableType.ALL;

    /** Mảng categoryId/productId áp dụng. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applicable_ids")
    private List<UUID> applicableIds;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
