package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Response.Notification.NotificationPageResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationResponse;
import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Repository.NotificationRepo;
import com.ice.notificationservice.Util.NotificationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepo notificationRepo;

    public NotificationPageResponse getNotification(int page, int size, Boolean isRead, NotificationType type, String userId) {

        UUID currentUserId = UUID.fromString(userId);
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);

        Specification<Notification> specification = Specification
                .where(NotificationSpecification.hasUserId(currentUserId))
                .and(NotificationSpecification.hasIsRead(isRead))
                .and(NotificationSpecification.hasNotificationType(type));

        PageRequest pageRequest = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> notifications = notificationRepo.findAll(specification, pageRequest);

        long unread = notificationRepo.countByUserIdAndIsRead(currentUserId, false);

        return new NotificationPageResponse(
                notifications.getContent().stream()
                        .map(notification -> new NotificationResponse(
                                notification.getId().toString(),
                                notification.getType().name(),
                                notification.getTitle(),
                                notification.getBody(),
                                notification.getImageUrl(),
                                notification.getActionUrl(),
                                notification.getIsRead(),
                                notification.getCreatedAt().toInstant(ZoneOffset.UTC)
                        )).toList(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                unread
        );
    }

    private int normalizeSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
