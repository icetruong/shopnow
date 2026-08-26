package com.ice.orderservice.Exception;

public class InventoryReserveFailedException extends RuntimeException {
    public InventoryReserveFailedException(String message) {
        super(message);
    }
}
