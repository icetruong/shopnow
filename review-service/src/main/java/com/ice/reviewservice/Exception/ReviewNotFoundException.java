package com.ice.reviewservice.Exception;

public class ReviewNotFoundException extends RuntimeException {
  public ReviewNotFoundException(String message) {
    super(message);
  }
}
