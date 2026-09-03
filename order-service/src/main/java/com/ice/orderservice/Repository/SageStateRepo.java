package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.SagaStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SageStateRepo extends JpaRepository<SagaState, UUID> {
    Optional<SagaState> findByOrderId(UUID orderId);

    @Query("""
            SELECT s FROM SagaState s
            WHERE s.sagaStatus IN :statuses
            AND s.updatedAt < :threshold
            ORDER BY s.updatedAt ASC 
""")
    List<SagaState> findStuck(@Param("statuses") List<SagaStatus> statuses,
                              @Param("threshold") LocalDateTime threshold,
                              Pageable pageable);
}
