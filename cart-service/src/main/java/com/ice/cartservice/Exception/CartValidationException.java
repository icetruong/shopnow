package com.ice.cartservice.Exception;

public class CartValidationException extends RuntimeException {
  public CartValidationException(String message) {
    super(message);
  }
}
