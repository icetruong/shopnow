package com.ice.inventoryservice.Exception;

public class FlashSaleUserLimitException extends RuntimeException {
    public FlashSaleUserLimitException() {
        super("Bạn đã mua sản phẩm này trong flash sale.");
    }
}