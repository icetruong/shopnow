package com.ice.promotionservice.Enum;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ ở các endpoint admin coupon (tạo / cập nhật). */
public enum CouponAdminError {
    CODE_DUPLICATED(HttpStatus.CONFLICT, "Mã coupon đã tồn tại."),
    TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "endsAt phải sau startsAt và sau thời điểm hiện tại."),
    DISCOUNT_VALUE_INVALID(HttpStatus.BAD_REQUEST, "discountValue không hợp lệ cho loại giảm giá này."),
    APPLICABLE_IDS_REQUIRED(HttpStatus.BAD_REQUEST, "applicableType CATEGORY/PRODUCT bắt buộc phải có applicableIds."),
    CODE_IMMUTABLE(HttpStatus.CONFLICT, " body gửi code khác code hiện tại"),
    USAGE_LIMIT_BELOW_USED(HttpStatus.BAD_REQUEST, "usageLimit mới < usedCount"),
    COUPON_ALREADY_INACTIVE(HttpStatus.CONFLICT, "Coupon đã bị vô hiệu hóa");


    private final HttpStatus status;
    private final String message;

    CouponAdminError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
