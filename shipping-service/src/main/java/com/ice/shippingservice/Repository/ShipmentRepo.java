package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepo extends JpaRepository<Shipment, UUID>, JpaSpecificationExecutor<Shipment> {
    Optional<Shipment> findByTrackingCode(String trackingCode);
    Optional<Shipment> findByOrderId(UUID orderId);

    @Query("""
    select s.id from Shipment s
    where s.status = com.ice.shippingservice.Enum.ShipmentStatus.PENDING
      and s.retryCount < :maxAttempts
    order by s.createdAt asc
""")
    List<UUID> findPendingIdsForRetry(@Param("maxAttempts") int maxAttempts, Pageable pageable);
}
