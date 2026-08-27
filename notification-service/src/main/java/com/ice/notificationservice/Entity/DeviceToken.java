package com.ice.notificationservice.Entity;

import com.ice.notificationservice.Enum.DeviceTokenFlatform;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "device_tokens",
        indexes = {
                @Index(name = "idx_device_tokens_token", columnList = "device_token", unique = true),
                @Index(name = "idx_device_tokens_user_id", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** FCM token. */
    @Column(name = "device_token", nullable = false, unique = true, columnDefinition = "TEXT")
    private String deviceToken;

    /** ANDROID / IOS / WEB. */
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DeviceTokenFlatform platform;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();;
    }
}
