package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.Order;
import lombok.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepo extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    @EntityGraph(attributePaths = {"orderItem", "orderStatusHistories"})
    @NonNull
    Optional<Order> findById(UUID id);
}
