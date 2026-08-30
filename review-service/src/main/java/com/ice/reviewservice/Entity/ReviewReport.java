package com.ice.reviewservice.Entity;

import com.ice.reviewservice.Enum.ReviewReportReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "review_reports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "idx_review_reports_unique",
                        columnNames = {"review_id", "user_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewReport {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_reports_review"))
    @OnDelete(action = OnDeleteAction.CASCADE) // khớp với "ON DELETE CASCADE" ở DB
    private Review review;

    /** Người báo cáo. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SPAM / OFFENSIVE / FAKE / IRRELEVANT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private ReviewReportReason reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
