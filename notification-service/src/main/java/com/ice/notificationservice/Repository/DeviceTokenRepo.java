package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeviceTokenRepo extends JpaRepository<DeviceToken, UUID> {
}
