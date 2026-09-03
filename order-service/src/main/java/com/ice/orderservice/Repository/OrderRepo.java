package com.ice.orderservice.Repository;

import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Enum.OrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import lombok.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepo extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {
    @EntityGraph(attributePaths = {"orderItems", "orderStatusHistories"})
    @NonNull
    Optional<Order> findById(UUID id);

    @EntityGraph(attributePaths = {"orderItems", "orderStatusHistories"})
    List<Order> findAllByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);

    /**
     * Khóa dòng order (SELECT ... FOR UPDATE) — mọi luồng SỬA order (HTTP cancel/update-status,
     * Kafka listener, saga recovery) phải nạp order qua đây để nối tiếp nhau, tránh lost update.
     * Không JOIN FETCH: PostgreSQL không cho FOR UPDATE trên nhánh nullable của outer join;
     * các collection lazy sẽ tự nạp (đã có @BatchSize) trong transaction.
     * lock.timeout = 3s: chờ tối đa 3s rồi ném LockTimeoutException thay vì treo vô hạn.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}
