package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockTransactionRepo extends JpaRepository<StockTransaction, UUID> {
}
