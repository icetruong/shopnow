package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CouponUsageRepo extends JpaRepository<CouponUsage, UUID> {
}
