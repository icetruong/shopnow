package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlashSaleRepo extends JpaRepository<FlashSale, UUID> {
}
