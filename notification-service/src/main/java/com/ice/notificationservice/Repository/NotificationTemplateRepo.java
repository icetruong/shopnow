package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.NotificationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationTemplateRepo extends JpaRepository<NotificationTemplate, UUID> {
}
