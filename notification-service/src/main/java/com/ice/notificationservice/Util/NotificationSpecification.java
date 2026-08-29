package com.ice.notificationservice.Util;

import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.UUID;

public class NotificationSpecification {

    public static Specification<Notification> hasUserId(UUID userId)
    {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<Notification> hasIsRead(Boolean isRead)
    {
        return (root, query, cb) ->
                isRead == null
                        ? null
                        : cb.equal(root.get("isRead"), isRead);
    }

    public static Specification<Notification> hasNotificationType(NotificationType type)
    {
        return (root, query, cb) ->
                type == null
                        ? null
                        : cb.equal(root.get("type"), type);

    }

    public static Specification<Notification> hasNotificationChannel(NotificationChannel channel)
    {
        return (root, query, cb) ->
                channel == null
                        ? null
                        : cb.equal(root.get("channel"), channel);
    }

    public static Specification<Notification> hasNotificationStatus(NotificationStatus status)
    {
        return (root, query, cb) ->
                status == null
                        ? null
                        : cb.equal(root.get("status"), status);
    }

    public static Specification<Notification> greaterDate(LocalDateTime dateTime)
    {
        return (root, query, cb) ->
                dateTime == null
                        ? null
                        : cb.greaterThanOrEqualTo(root.get("createdAt"), dateTime);
    }
}
