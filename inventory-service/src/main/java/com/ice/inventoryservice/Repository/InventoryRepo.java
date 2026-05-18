package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepo extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByVariantId(UUID variantId);
    List<Inventory> findAllByVariantIdIn(List<UUID> variantIds);
}
