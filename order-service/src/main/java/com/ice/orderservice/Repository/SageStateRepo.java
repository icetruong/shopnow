package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SageStateRepo extends JpaRepository<SagaState, UUID> {
    Optional<SagaState> findByOrderId(UUID orderId);
}
