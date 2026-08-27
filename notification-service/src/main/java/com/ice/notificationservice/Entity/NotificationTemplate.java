package com.ice.notificationservice.Entity;

import com.ice.notificationservice.Enum.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_notification_templates_code", columnNames = "code")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    /** EMAIL / SMS / PUSH. */
    @Column(name = "channel", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    /** Tiêu đề (email). */
    @Column(name = "subject", length = 255)
    private String subject;

    /** Nội dung có placeholder {{orderCode}}. */
    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    private String bodyTemplate;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
