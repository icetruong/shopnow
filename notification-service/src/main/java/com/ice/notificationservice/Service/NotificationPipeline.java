package com.ice.notificationservice.Service;

import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Repository.NotificationRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Khung xử lý dùng chung cho MỌI consumer notification.
 * Mỗi XxxEventNotificationService chỉ còn phải: check {@link #alreadyProcessed},
 * xác định người nhận + build {@code vars}, rồi gọi {@link #deliver}.
 *
 * Các bước 1, 4, 7, 8, 6 nằm hết ở đây:
 *  1. Idempotency check (alreadyProcessed)
 *  4. Lọc kênh theo notification_preferences
 *  7. INSERT Notification (status = PENDING)
 *  8. Redis markProcessed
 *  6. Đẩy sang NotificationSender (async)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPipeline {

    private final IdempotencyService idempotencyService;
    private final NotificationPreferenceGateService preferenceGate;
    private final NotificationRepo notificationRepo;
    private final NotificationSender notificationSender;

    /** Bước 1: true -> handler return luôn, khỏi gọi User/Order service. */
    public boolean alreadyProcessed(String eventId) {
        if (idempotencyService.isProcessed(eventId)) {
            log.info("Event {} đã xử lý, bỏ qua", eventId);
            return true;
        }
        return false;
    }

    /** Notification thường: kênh EMAIL/SMS/PUSH bị lọc theo notification_preferences. */
    public void deliver(String eventId, NotificationType type, UUID userId,
                        String actionUrl, List<NotificationDraft.Message> messages) {
        deliverInternal(eventId, type, userId, actionUrl, messages, true);
    }

    /**
     * Email/SMS giao dịch & vận hành (reset mật khẩu, biên nhận, cảnh báo admin):
     * gửi bất kể user bật/tắt preference gì.
     */
    public void deliverAlways(String eventId, NotificationType type, UUID userId,
                              String actionUrl, List<NotificationDraft.Message> messages) {
        deliverInternal(eventId, type, userId, actionUrl, messages, false);
    }

    private void deliverInternal(String eventId, NotificationType type, UUID userId,
                                 String actionUrl, List<NotificationDraft.Message> messages,
                                 boolean applyPreferenceGate) {

        List<Notification> toSave = new ArrayList<>();
        for (NotificationDraft.Message m : messages) {
            // IN_APP luôn gửi; kênh khác phải qua cửa preference (bước 4) trừ khi là mail giao dịch
            if (applyPreferenceGate
                    && m.channel() != NotificationChannel.IN_APP
                    && !preferenceGate.allows(userId, type, m.channel())) {
                log.info("User {} tắt {}/{} -> bỏ kênh này", userId, type, m.channel());
                continue;
            }
            toSave.add(Notification.builder()
                    .userId(userId)
                    .channel(m.channel())
                    .type(type)
                    .title(m.title())
                    .body(m.body())
                    .actionUrl(actionUrl)
                    .recipient(m.recipient())
                    .status(NotificationStatus.PENDING)
                    .refEventId(parseUuidOrNull(eventId))
                    .build());
        }

        if (!toSave.isEmpty()) {
            List<Notification> saved = notificationRepo.saveAll(toSave);   // bước 7
            idempotencyService.markProcessed(eventId);                     // bước 8
            saved.forEach(n -> notificationSender.dispatch(n.getId()));    // bước 6 (async)
        } else {
            // Không có kênh nào để gửi (user tắt hết) -> vẫn coi như đã xử lý xong.
            idempotencyService.markProcessed(eventId);
        }
    }

    private static UUID parseUuidOrNull(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
