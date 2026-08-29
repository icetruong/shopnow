package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface NotificationRepo extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    long countByUserIdAndIsRead(UUID userId, Boolean isRead);
}
