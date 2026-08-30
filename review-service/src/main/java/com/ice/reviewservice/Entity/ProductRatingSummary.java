package com.ice.reviewservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_rating_summary")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRatingSummary {
    @Id
    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Builder.Default
    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    /** Tổng điểm (để tính avg nhanh). */
    @Builder.Default
    @Column(name = "sum_rating", nullable = false)
    private Long sumRating = 0L;

    /** sum_rating / total_reviews. */
    @Builder.Default
    @Column(name = "avg_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "count_5", nullable = false)
    private Integer count5 = 0;

    @Builder.Default
    @Column(name = "count_4", nullable = false)
    private Integer count4 = 0;

    @Builder.Default
    @Column(name = "count_3", nullable = false)
    private Integer count3 = 0;

    @Builder.Default
    @Column(name = "count_2", nullable = false)
    private Integer count2 = 0;

    @Builder.Default
    @Column(name = "count_1", nullable = false)
    private Integer count1 = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
