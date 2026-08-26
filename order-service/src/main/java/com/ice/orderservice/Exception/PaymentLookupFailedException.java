package com.ice.orderservice.Exception;

public class PaymentLookupFailedException extends RuntimeException {
    public PaymentLookupFailedException(String message) {
        super(message);
    }
}
