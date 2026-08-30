package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface DeviceTokenRepo extends JpaRepository<DeviceToken, UUID> {
    Optional<DeviceToken> findByDeviceToken(String deviceToken);

    long deleteByDeviceTokenAndUserId(String deviceToken, UUID userId);

    @Query("""
        SELECT COUNT (DISTINCT d.userId)
        FROM DeviceToken d
        WHERE d.isActive = true
""")
    long countDistinctActiveUser();
}
