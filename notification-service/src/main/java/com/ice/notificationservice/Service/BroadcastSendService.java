package com.ice.notificationservice.Service;

import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Entity.NotificationBroadcast;
import com.ice.notificationservice.Enum.BroadcastStatus;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Repository.DeviceTokenRepo;
import com.ice.notificationservice.Repository.NotificationBroadcastRepo;
import com.ice.notificationservice.Repository.NotificationRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastSendService {

    private static final int BATCH_SIZE = 500;

    private final NotificationBroadcastRepo notificationBroadcastRepo;
    private final DeviceTokenRepo deviceTokenRepo;
    private final NotificationPreferenceGateService notificationPreferenceGateService;
    private final NotificationRepo notificationRepo;

    public void send(UUID broadcastId)
    {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        if(notificationBroadcastRepo.claim(broadcastId, now) == 0)
        {
            log.debug("Broadcast {} đã bị lấy / không còn SCHEDULED", broadcastId);
            return;
        }

        NotificationBroadcast broadcast = notificationBroadcastRepo.findById(broadcastId).orElseThrow();

        // 2. EMAIL/SMS chưa hỗ trợ (thiếu contact info)
        if (broadcast.getChannel() == NotificationChannel.EMAIL || broadcast.getChannel() == NotificationChannel.SMS) {
            log.warn("Broadcast {} kênh {} chưa hỗ trợ -> FAILED", broadcastId, broadcast.getChannel());
            finish(broadcast, BroadcastStatus.FAILED, 0, 0);
            return;
        }

        int sent = 0;
        int skipped = 0;
        int page = 0;

        while (true)
        {
            Slice<UUID> userIds = deviceTokenRepo.findActiveUserIds(PageRequest.of(page, BATCH_SIZE));

            List<Notification> notifications = new ArrayList<>();
            for(UUID userId : userIds.getContent())
            {
                // Broadcast = PROMOTION -> phải tôn trọng preference (khác mail giao dịch)
                if (!notificationPreferenceGateService.allows(userId, NotificationType.PROMOTION, broadcast.getChannel())) {
                    skipped++;
                    continue;
                }
                notifications.add(Notification.builder()
                        .userId(userId)
                        .channel(broadcast.getChannel())
                        .type(NotificationType.PROMOTION)
                        .title(broadcast.getTitle())
                        .body(broadcast.getBody())
                        .imageUrl(broadcast.getImageUrl())
                        .actionUrl(broadcast.getActionUrl())
                        .status(NotificationStatus.SENT)
                        .sentAt(now)
                        .refEventId(broadcast.getId())
                        .build()
                );
            }

            if(!notifications.isEmpty())
            {
                notificationRepo.saveAll(notifications);
                sent += notifications.size();
                broadcast.setSentCount(sent);
                notificationBroadcastRepo.save(broadcast);
            }

            if (!userIds.hasNext())
                break;
            page++;
        }

        finish(broadcast, sent == 0 ? BroadcastStatus.FAILED : BroadcastStatus.SENT, sent, 0);
        log.info("Broadcast {} xong: sent={}, bỏ qua (preference)={}", broadcastId, sent, skipped);
    }

    /** Broadcast treo ở SENDING quá lâu -> đóng lại để worker không nhặt lại mãi. */
    public void markStale(UUID broadcastId) {
        notificationBroadcastRepo.findById(broadcastId).ifPresent(b -> finish(
                b,
                b.getSentCount() > 0 ? BroadcastStatus.PARTIALLY_FAILED : BroadcastStatus.FAILED,
                b.getSentCount(),
                b.getFailedCount()));
    }

    private void finish(NotificationBroadcast broadcast, BroadcastStatus status, int sent, int failed)
    {
        broadcast.setStatus(status);
        broadcast.setSentCount(sent);
        broadcast.setFailedCount(failed);
        broadcast.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        notificationBroadcastRepo.save(broadcast);
    }
}
