package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockReservationRepo extends JpaRepository<StockReservation, UUID> {
}
