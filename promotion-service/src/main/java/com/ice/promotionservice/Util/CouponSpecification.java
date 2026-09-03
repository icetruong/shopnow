package com.ice.promotionservice.Util;

import com.ice.promotionservice.Entity.Coupon;
import com.ice.promotionservice.Enum.CouponStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class CouponSpecification {

    private CouponSpecification() {}

    /**
     * Dịch status + keyword thành điều kiện WHERE trên is_active / starts_at / ends_at / code / title.
     * status = null nghĩa là không lọc theo trạng thái (ALL).
     * status suy ra theo thứ tự ưu tiên: INACTIVE > SCHEDULED > EXPIRED > ACTIVE.
     */
    public static Specification<Coupon> filter(CouponStatus status, String keyword, LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (status != null) {
                switch (status) {
                    case INACTIVE -> ps.add(cb.equal(root.get("isActive"), false));
                    case SCHEDULED -> {
                        ps.add(cb.equal(root.get("isActive"), true));
                        ps.add(cb.greaterThan(root.<LocalDateTime>get("startsAt"), now));
                    }
                    case EXPIRED -> {
                        ps.add(cb.equal(root.get("isActive"), true));
                        ps.add(cb.lessThan(root.<LocalDateTime>get("endsAt"), now));
                    }
                    case ACTIVE -> {
                        ps.add(cb.equal(root.get("isActive"), true));
                        ps.add(cb.lessThanOrEqualTo(root.<LocalDateTime>get("startsAt"), now));
                        ps.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("endsAt"), now));
                    }
                }
            }

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.<String>get("code")), like),
                        cb.like(cb.lower(root.<String>get("title")), like)
                ));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
