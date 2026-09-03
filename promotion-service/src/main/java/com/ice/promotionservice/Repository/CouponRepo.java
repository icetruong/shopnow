package com.ice.promotionservice.Repository;

import com.ice.promotionservice.Entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CouponRepo extends JpaRepository<Coupon, UUID> {
    Optional<Coupon> findByCode(String code);

    List<Coupon> findAllByIsActiveTrueAndStartsAtBeforeAndEndsAtAfter(LocalDateTime startsAtBefore, LocalDateTime endsAtAfter);
}
