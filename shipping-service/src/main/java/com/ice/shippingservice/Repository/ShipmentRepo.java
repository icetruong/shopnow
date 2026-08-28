package com.ice.shippingservice.Repository;

import com.ice.shippingservice.Entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepo extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByTrackingCode(String trackingCode);
    Optional<Shipment> findByOrderId(UUID orderId);
}
