package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.FlashSaleStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlashSaleStockRepo extends JpaRepository<FlashSaleStock, UUID> {
    boolean existsByFlashSaleId(UUID flashSaleId);
    List<FlashSaleStock> findAllByFlashSaleId(UUID flashSaleId);

    List<FlashSaleStock> findAllBySettledAtIsNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FlashSaleStock f where f.id = :id")
    Optional<FlashSaleStock> findByIdForUpdate(@Param("id") UUID id);
}
