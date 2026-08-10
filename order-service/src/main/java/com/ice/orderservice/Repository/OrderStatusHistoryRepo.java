package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderStatusHistoryRepo extends JpaRepository<OrderStatusHistory, UUID> {
}
