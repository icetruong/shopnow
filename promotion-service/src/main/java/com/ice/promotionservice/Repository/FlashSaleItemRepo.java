package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.FlashSaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleItemRepo extends JpaRepository<FlashSaleItem, UUID> {
    List<FlashSaleItem> findAllByFlashSaleId(UUID flashSaleId);

    Optional<FlashSaleItem> findByIdAndFlashSaleIdAndVariantId(UUID id, UUID flashSaleId, UUID variantId);
}
