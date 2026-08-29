package com.ice.notificationservice.Util;

import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationType;
import org.springframework.data.jpa.domain.Specification;

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
}
