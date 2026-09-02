package com.ice.reviewservice.Repository;

import com.ice.reviewservice.Entity.ProductRatingSummary;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRatingSummaryRepo extends JpaRepository<ProductRatingSummary, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s 
        FROM ProductRatingSummary s
        WHERE s.productId = :productId
""")
    Optional<ProductRatingSummary> findByIdForUpdate(@Param("productId") UUID productId);
}
