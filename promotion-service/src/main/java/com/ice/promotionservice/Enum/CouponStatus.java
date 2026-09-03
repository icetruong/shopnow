package com.ice.promotionservice.Enum;

/**
 * Trạng thái suy ra của coupon (KHÔNG lưu DB) — tính từ is_active + starts_at + ends_at.
 * Dùng cho field `status` ở response và cho filter `?status=` của GET /admin/coupons.
 */
public enum CouponStatus {
    ACTIVE,
    INACTIVE,
    SCHEDULED,
    EXPIRED
}
