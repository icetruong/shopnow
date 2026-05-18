package com.ice.inventoryservice.Enum;

public enum StockTransactionType {
    IMPORT,      // Nhập hàng vào kho
    DEDUCT,      // Trừ hàng sau khi đơn thành công
    RELEASE,     // Hoàn lại hàng (hủy đơn)
    ADJUSTMENT,  // Điều chỉnh thủ công bởi admin
    FLASH_SALE   // Bán qua flash sale
}
