package com.ice.paymentservice.Repository;

import com.ice.paymentservice.Entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepo extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {
    boolean existsByOrderId(UUID orderId);

    Optional<Payment> findByIdAndUserId(UUID id ,UUID userId);

    Optional<Payment> findByOrderId(UUID orderId);
}
