package com.ice.paymentservice.Repository;

import com.ice.paymentservice.Entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefundRepo extends JpaRepository<Refund, UUID> {
    boolean existsByOrderId(UUID orderId);
}
