package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface OrderRepo extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
}
