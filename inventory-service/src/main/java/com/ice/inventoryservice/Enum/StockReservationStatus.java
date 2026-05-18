package com.ice.inventoryservice.Enum;

public enum StockReservationStatus {
    RESERVED,   // Đang giữ chờ thanh toán
    DEDUCTED,   // Đã trừ kho sau khi thanh toán thành công
    RELEASED,   // Đã hoàn lại (hủy đơn / timeout)
    EXPIRED     // Hết hạn giữ hàng
}
