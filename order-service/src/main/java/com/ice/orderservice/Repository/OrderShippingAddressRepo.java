package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.OrderShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderShippingAddressRepo extends JpaRepository<OrderShippingAddress, UUID> {
    Optional<OrderShippingAddress> findByOrderId(UUID orderId);
}
