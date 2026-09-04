package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FlashSaleItemRepo extends JpaRepository<FlashSaleItem, UUID> {
    List<FlashSaleItem> findAllByFlashSaleId(UUID flashSaleId);
}
