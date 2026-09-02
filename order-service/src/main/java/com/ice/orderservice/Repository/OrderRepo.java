package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Enum.OrderStatus;
import lombok.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepo extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    @EntityGraph(attributePaths = {"orderItems", "orderStatusHistories"})
    @NonNull
    Optional<Order> findById(UUID id);

    @EntityGraph(attributePaths = {"orderItems", "orderStatusHistories"})
    List<Order> findAllByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);
}
