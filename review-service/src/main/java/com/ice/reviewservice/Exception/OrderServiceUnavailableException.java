package com.ice.reviewservice.Exception;

public class OrderServiceUnavailableException extends RuntimeException {
  public OrderServiceUnavailableException(String message) {
    super(message);
  }
}
