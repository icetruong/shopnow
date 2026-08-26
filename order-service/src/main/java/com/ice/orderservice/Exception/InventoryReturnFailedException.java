package com.ice.orderservice.Exception;

public class InventoryReturnFailedException extends RuntimeException {
    public InventoryReturnFailedException(String message) {
        super(message);
    }
}
