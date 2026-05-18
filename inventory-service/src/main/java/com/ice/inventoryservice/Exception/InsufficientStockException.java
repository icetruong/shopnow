package com.ice.inventoryservice.Exception;

public class InsufficientStockException extends RuntimeException {
  public InsufficientStockException(String message) {
    super(message);
  }
}
