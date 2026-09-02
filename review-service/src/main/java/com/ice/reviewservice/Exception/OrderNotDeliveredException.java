package com.ice.reviewservice.Exception;

public class OrderNotDeliveredException extends RuntimeException {
  public OrderNotDeliveredException(String message) {
    super(message);
  }
}
