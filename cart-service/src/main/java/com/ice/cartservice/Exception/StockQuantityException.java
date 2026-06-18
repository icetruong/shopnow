package com.ice.cartservice.Exception;

public class StockQuantityException extends RuntimeException {
  public StockQuantityException(String message) {
    super(message);
  }
}
