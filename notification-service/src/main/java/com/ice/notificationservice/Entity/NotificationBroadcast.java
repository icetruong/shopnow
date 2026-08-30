package com.ice.notificationservice.Entity;

import com.ice.notificationservice.Enum.BroadcastStatus;
import com.ice.notificationservice.Enum.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "notification_broadcasts",
        indexes = {
                @Index(name = "idx_notification_broadcasts_status", columnList = "status"),
                @Index(name = "idx_notification_broadcasts_scheduled_at", columnList = "scheduled_at"),
                @Index(name = "idx_notification_broadcasts_target", columnList = "target")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationBroadcast {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20, updatable = false)
    private NotificationChannel channel;

    /** Tập người nhận: "ALL_USERS" hoặc mã segment. */
    @Column(name = "target", nullable = false, length = 100)
    private String target;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Link khi bấm vào notification. */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Builder.Default
    @Column(name = "estimated_reach", nullable = false)
    private Integer estimatedReach = 0;

    @Builder.Default
    @Column(name = "sent_count", nullable = false)
    private Integer sentCount = 0;

    @Builder.Default
    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /** SCHEDULED / SENDING / SENT / PARTIALLY_FAILED / FAILED / CANCELLED. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BroadcastStatus status = BroadcastStatus.SCHEDULED;

    /** Thời điểm job bắt đầu gửi. */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** Thời điểm job gửi xong. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
