package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.FlashSalePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FlashSalePurchaseRepo extends JpaRepository<FlashSalePurchase, UUID> {
    Optional<FlashSalePurchase> findByFlashSaleIdAndOrderId(UUID flashSaleId, UUID orderId);
}
