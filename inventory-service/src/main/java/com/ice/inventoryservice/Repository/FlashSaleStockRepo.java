package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.FlashSaleStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashSaleStockRepo extends JpaRepository<FlashSaleStock, UUID> {
    boolean existsByFlashSaleId(UUID flashSaleId);
    List<FlashSaleStock> findAllByFlashSaleId(UUID flashSaleId);
}
