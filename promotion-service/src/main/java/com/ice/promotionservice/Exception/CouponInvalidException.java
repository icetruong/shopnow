package com.ice.promotionservice.Exception;

public class CouponInvalidException extends RuntimeException {
  public CouponInvalidException(String message) {
    super(message);
  }
}
