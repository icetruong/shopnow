package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.ShipmentTracking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ShipmentTrackingRepo extends JpaRepository<ShipmentTracking, UUID> {
}
