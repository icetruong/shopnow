package com.ice.promotionservice.Enum;

public enum CouponInvalidReason {
    NOT_FOUND("Mã giảm giá không tồn tại."),
    NOT_STARTED("Mã giảm giá chưa đến thời gian áp dụng."),
    EXPIRED("Mã giảm giá đã hết hạn."),
    USAGE_LIMIT_REACHED("Mã giảm giá đã hết lượt sử dụng."),
    USER_LIMIT_REACHED("Bạn đã dùng hết lượt cho mã này."),
    MIN_ORDER_NOT_MET("Đơn hàng chưa đạt giá trị tối thiểu để dùng mã."),
    NOT_APPLICABLE("Mã giảm giá không áp dụng cho sản phẩm trong giỏ hàng.");

    private final String message;
    CouponInvalidReason(String message) { this.message = message; }
    public String getMessage() { return message; }
}
