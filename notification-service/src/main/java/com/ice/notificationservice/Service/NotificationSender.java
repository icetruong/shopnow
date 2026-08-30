package com.ice.notificationservice.Service;

import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Repository.NotificationRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSender {
    private final NotificationRepo notificationRepo;

    @Async("notificationExecutor")
    @Transactional
    public void dispatch(UUID notificationId)
    {
        Notification n = notificationRepo.findById(notificationId).orElse(null);
        if (n == null)
        {
            log.warn("Không thấy notification {}", notificationId);
            return;
        }

        try {
            deliver(n);
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(LocalDateTime.now());
        }
        catch (RuntimeException ex) {
            log.error("Gửi notification {} lỗi", notificationId, ex);
            n.setStatus(NotificationStatus.FAILED);
            n.setRetryCount(n.getRetryCount() + 1);
        }
        notificationRepo.save(n);
    }

    private void deliver(Notification n) {
        // TODO: tích hợp SMTP / Twilio / FCM. Tạm log để chạy end-to-end.
        log.info("[{}] -> {} | {} | {}", n.getChannel(), n.getRecipient(), n.getTitle(), n.getBody());
    }
}
