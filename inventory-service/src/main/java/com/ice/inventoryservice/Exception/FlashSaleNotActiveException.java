package com.ice.inventoryservice.Exception;

public class FlashSaleNotActiveException extends RuntimeException {
    public FlashSaleNotActiveException() {
        super("Flash sale chưa bắt đầu hoặc đã kết thúc.");
    }
}
