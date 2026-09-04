package com.ice.inventoryservice.Exception;

public class FlashSaleUserLimitException extends RuntimeException {
    public FlashSaleUserLimitException() {
        super("Bạn đã mua đủ giới hạn trong flash sale này.");
    }
}