package com.ice.inventoryservice.Exception;

public class InventoryExistException extends RuntimeException {
    public InventoryExistException(String message) {
        super(message);
    }
}
