package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.CouponUsage;
import com.ice.promotionservice.Enum.CouponUsageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CouponUsageRepo extends JpaRepository<CouponUsage, UUID> {
    int countByUserIdAndCouponIdAndStatus(UUID userId, UUID couponId, CouponUsageStatus status);

    Optional<CouponUsage> findByCouponCodeAndOrderId(String couponCode, UUID orderId);
}
