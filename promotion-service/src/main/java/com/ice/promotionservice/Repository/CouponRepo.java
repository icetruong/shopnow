package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepo extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {
    Optional<Coupon> findByCode(String code);

    List<Coupon> findAllByIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(LocalDateTime startsAtBefore, LocalDateTime endsAtAfter);

    /** Đếm coupon đang ACTIVE: is_active = true AND starts_at <= now <= ends_at. */
    long countByIsActiveTrueAndStartsAtLessThanEqualAndEndsAtGreaterThanEqual(LocalDateTime now1, LocalDateTime now2);

    /** Đếm coupon đã EXPIRED: is_active = true AND now > ends_at. */
    long countByIsActiveTrueAndEndsAtLessThan(LocalDateTime now);
}
