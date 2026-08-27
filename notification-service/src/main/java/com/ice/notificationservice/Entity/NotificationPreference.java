package com.ice.notificationservice.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_notification_preferences_user_id", columnNames = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Builder.Default
    @Column(name = "email_order_updates", nullable = false)
    private Boolean emailOrderUpdates = true;

    @Builder.Default
    @Column(name = "email_promotions", nullable = false)
    private Boolean emailPromotions = true;

    @Builder.Default
    @Column(name = "email_payment_receipt", nullable = false)
    private Boolean emailPaymentReceipt = true;

    @Builder.Default
    @Column(name = "sms_order_updates", nullable = false)
    private Boolean smsOrderUpdates = false;

    @Builder.Default
    @Column(name = "sms_delivery_alert", nullable = false)
    private Boolean smsDeliveryAlert = true;

    @Builder.Default
    @Column(name = "push_order_updates", nullable = false)
    private Boolean pushOrderUpdates = true;

    @Builder.Default
    @Column(name = "push_promotions", nullable = false)
    private Boolean pushPromotions = true;

    @Builder.Default
    @Column(name = "push_flash_sale", nullable = false)
    private Boolean pushFlashSale = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
