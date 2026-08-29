package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepo extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {
    long countByUserIdAndIsRead(UUID userId, Boolean isRead);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    List<Notification> findAllByUserId(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE Notification n
        SET n.isRead = true 
        WHERE n.userId = :userId AND n.isRead = false
""")
    int markAllReadByUserId(@Param("userId") UUID userId);

    long deleteByIdAndUserId(UUID id, UUID userId);
}
