package com.qlda.userservice.Exception;

public class VerifyTokenInvalidException extends RuntimeException {
  public VerifyTokenInvalidException(String message) {
    super(message);
  }
}
