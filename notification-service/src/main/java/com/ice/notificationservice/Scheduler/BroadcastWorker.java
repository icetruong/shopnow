package com.ice.notificationservice.Scheduler;

import com.ice.notificationservice.Entity.NotificationBroadcast;
import com.ice.notificationservice.Enum.BroadcastStatus;
import com.ice.notificationservice.Repository.NotificationBroadcastRepo;
import com.ice.notificationservice.Service.BroadcastSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BroadcastWorker {
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final NotificationBroadcastRepo notificationBroadcastRepo;
    private final BroadcastSendService broadcastSendService;

    @Scheduled(fixedDelay = 10_000)
    public void pollDueBroadcasts()
    {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // tới giờ gửi
        for (NotificationBroadcast b : notificationBroadcastRepo
                .findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(BroadcastStatus.SCHEDULED, now)) {
            runSafely(b.getId());
        }

        for (NotificationBroadcast b : notificationBroadcastRepo
                .findTop20ByStatusAndStartedAtLessThan(BroadcastStatus.SENDING, now.minus(STALE_AFTER))) {
            log.warn("Broadcast {} treo ở SENDING từ {} -> đóng lại", b.getId(), b.getStartedAt());
            try {
                broadcastSendService.markStale(b.getId());
            } catch (Exception e) {
                log.error("markStale {} lỗi", b.getId(), e);
            }
        }
    }

    private void runSafely(UUID broadcastId) {
        try {
            broadcastSendService.send(broadcastId);   // 1 cái lỗi không chặn cái sau
        } catch (Exception e) {
            log.error("Gửi broadcast {} lỗi", broadcastId, e);
        }
    }
}
