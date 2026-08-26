package com.ice.orderservice.Exception;

public class PaymentRefundFailException extends RuntimeException {
  public PaymentRefundFailException(String message) {
    super(message);
  }
}
