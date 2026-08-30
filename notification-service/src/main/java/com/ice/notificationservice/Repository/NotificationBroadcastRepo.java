package com.ice.notificationservice.Repository;

import com.ice.notificationservice.Entity.NotificationBroadcast;
import com.ice.notificationservice.Enum.BroadcastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationBroadcastRepo extends JpaRepository<NotificationBroadcast, UUID> {
    /** Broadcast tới giờ gửi. */
    List<NotificationBroadcast> findTop20ByStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            BroadcastStatus status, LocalDateTime cutoff);

    /** Broadcast treo ở SENDING quá lâu (instance cũ chết giữa chừng). */
    List<NotificationBroadcast> findTop20ByStatusAndStartedAtLessThan(
            BroadcastStatus status, LocalDateTime cutoff);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE NotificationBroadcast b                                                                                                          \s
                              SET b.status     = com.ice.notificationservice.Enum.BroadcastStatus.SENDING,                                                         \s
                                  b.startedAt  = :now,                                                                                                             \s
                                  b.updatedAt  = :now                                                                                                              \s
                            WHERE b.id = :id                                                                                                                       \s
                              AND b.status = com.ice.notificationservice.Enum.BroadcastStatus.SCHEDULED\s
        
""")
    int claim(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
