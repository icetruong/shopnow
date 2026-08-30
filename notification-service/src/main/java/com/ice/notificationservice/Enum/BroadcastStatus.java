package com.ice.notificationservice.Enum;

public enum BroadcastStatus {
    /** Đã tạo, chờ tới giờ gửi. */
    SCHEDULED,
    /** Job đang gửi. */
    SENDING,
    /** Đã gửi xong, không có lỗi. */
    SENT,
    /** Gửi xong nhưng có một phần thất bại. */
    PARTIALLY_FAILED,
    /** Toàn bộ thất bại. */
    FAILED,
    /** Bị huỷ trước khi gửi. */
    CANCELLED
}
