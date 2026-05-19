package com.ice.inventoryservice.Repository;

import com.ice.inventoryservice.Entity.StockTransaction;
import com.ice.inventoryservice.Enum.StockTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface StockTransactionRepo extends JpaRepository<StockTransaction, UUID> {


    @Query("""
      SELECT st FROM StockTransaction st
      WHERE st.createdAt >= :startDateTime AND st.createdAt < :endDateTime
      AND (:type IS NULL OR st.type = :type)
      AND st.variantId = :variantId
      """)

    Page<StockTransaction> findByDateRangeAndType(
            @Param("variantId") UUID variantId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("type") StockTransactionType type,
            Pageable pageable
    );


}
