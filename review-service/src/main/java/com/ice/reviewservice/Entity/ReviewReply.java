package com.ice.reviewservice.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "review_replies",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_review_replies_review_id", columnNames = "review_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewReply {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_review_replies_review"))
    @OnDelete(action = OnDeleteAction.CASCADE) // khớp với "ON DELETE CASCADE" ở DB
    private Review review;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** adminId. */
    @Column(name = "replied_by", nullable = false)
    private UUID repliedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
