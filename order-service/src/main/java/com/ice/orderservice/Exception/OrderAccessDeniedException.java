package com.ice.orderservice.Exception;

public class OrderAccessDeniedException extends RuntimeException {
  public OrderAccessDeniedException(String message) {
    super(message);
  }
}
