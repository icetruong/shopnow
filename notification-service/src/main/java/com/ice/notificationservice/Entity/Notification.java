package com.ice.notificationservice.Entity;

import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_user_id", columnList = "user_id"),
                @Index(name = "idx_notifications_status", columnList = "status"),
                @Index(name = "idx_notifications_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Người nhận. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** EMAIL / SMS / PUSH / IN_APP. */
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    /** ORDER / PAYMENT / SHIPMENT / PROMOTION / SYSTEM. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    /** Link khi bấm vào notification. */
    @Column(name = "action_url", length = 500)
    private String actionUrl;

    /** Email/phone thực tế đã gửi. */
    @Column(name = "recipient", length = 255)
    private String recipient;

    /** PENDING / SENT / FAILED / READ. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.PENDING;

    /** Chỉ áp dụng cho IN_APP. */
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @Builder.Default
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** eventId của Kafka event nguồn. */
    @Column(name = "ref_event_id")
    private UUID refEventId;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
