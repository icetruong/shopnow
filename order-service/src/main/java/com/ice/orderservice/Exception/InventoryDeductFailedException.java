package com.ice.orderservice.Exception;

public class InventoryDeductFailedException extends RuntimeException {
    public InventoryDeductFailedException(String message) {
        super(message);
    }
}
