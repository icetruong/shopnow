package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepo extends JpaRepository<Inventory, UUID>, JpaSpecificationExecutor<Inventory> {
    Optional<Inventory> findByVariantId(UUID variantId);
    List<Inventory> findAllByVariantIdIn(List<UUID> variantIds);

    boolean existsByVariantId(UUID variantId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Inventory> findAllByVariantIdInOrderByVariantId(List<UUID> variantIds);
}
