package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepo extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findByDeviceToken(String deviceToken);

    long deleteByDeviceTokenAndUserId(String deviceToken, UUID userId);
}
