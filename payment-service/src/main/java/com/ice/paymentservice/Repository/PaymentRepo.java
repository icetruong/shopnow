package com.ice.paymentservice.Repository;

import com.ice.paymentservice.Entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepo extends JpaRepository<Payment, UUID> {
    boolean existsByOrderId(UUID orderId);

    Optional<Payment> findByIdAndUserId(UUID id ,UUID userId);

    Optional<Payment> findByOrderId(UUID orderId);
}
