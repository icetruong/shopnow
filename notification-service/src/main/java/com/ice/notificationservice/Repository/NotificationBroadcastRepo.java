package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.NotificationBroadcast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationBroadcastRepo extends JpaRepository<NotificationBroadcast, UUID> {
}
