package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepo extends JpaRepository<Notification, UUID> {
}
