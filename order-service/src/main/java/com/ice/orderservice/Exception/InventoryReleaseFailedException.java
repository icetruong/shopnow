package com.ice.orderservice.Exception;

public class InventoryReleaseFailedException extends RuntimeException {
    public InventoryReleaseFailedException(String message) {
        super(message);
    }
}
