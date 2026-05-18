package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.StockReservation;
import com.ice.inventoryservice.Enum.StockReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockReservationRepo extends JpaRepository<StockReservation, UUID> {
    List<StockReservation> findAllByOrderIdAndStatus(UUID orderId, StockReservationStatus status);
}
