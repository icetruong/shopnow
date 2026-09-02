package com.ice.reviewservice.Exception;

public class AlreadyReviewedException extends RuntimeException {
  public AlreadyReviewedException(String message) {
    super(message);
  }
}
