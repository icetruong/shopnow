package com.ice.inventoryservice.Exception;

public class FlashSaleAlreadyExistsException extends RuntimeException {
    public FlashSaleAlreadyExistsException() {
        super("Flash sale này đã được khởi tạo tồn kho.");
    }
}
