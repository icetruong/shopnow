package com.ice.productservice.Exception;

public class TooManyImagesException extends RuntimeException {
  public TooManyImagesException(String message) {
    super(message);
  }
}
