package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepo extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUserId(UUID userId);
}
