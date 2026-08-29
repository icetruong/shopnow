package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Response.Notification.NotificationPageResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationUnreadCountResponse;
import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Exception.NotificationNotFoundException;
import com.ice.notificationservice.Repository.NotificationRepo;
import com.ice.notificationservice.Util.NotificationSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Cacheable(value = "noti:unread", key = "#userId")
    public NotificationUnreadCountResponse getUnreadCountNotification(String userId) {

        long unread = notificationRepo.countByUserIdAndIsRead(UUID.fromString(userId), false);

        return new NotificationUnreadCountResponse(unread);
    }

    @CacheEvict(value = "noti:unread", key = "#userId")
    public void markRead(String notificationId, String userId) {
        Notification notification = notificationRepo.findByIdAndUserId(UUID.fromString(notificationId), UUID.fromString(userId))
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        notification.setIsRead(true);
        notificationRepo.save(notification);
    }

    @Transactional
    @CacheEvict(value = "noti:unread", key = "#userId")
    public void markReadAll(String userId) {
        notificationRepo.markAllReadByUserId(UUID.fromString(userId));
    }

    @Transactional
    @CacheEvict(value = "noti:unread", key = "#userId")
    public void deleteNotification(String notificationId, String userId) {
        long deleted = notificationRepo.deleteByIdAndUserId(UUID.fromString(notificationId), UUID.fromString(userId));
        if (deleted == 0) {
            throw new NotificationNotFoundException("Notification not found: " + notificationId);
        }
    }

    private int normalizeSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
