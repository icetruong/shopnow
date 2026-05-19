package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.StockReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface StockReservationRepo extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findAllByOrderIdAndStatus(UUID orderId, StockReservationStatus status);
    List<StockReservation> findAllByStatusAndExpiresAtLessThan(StockReservationStatus status, LocalDateTime expiresAt);
}
