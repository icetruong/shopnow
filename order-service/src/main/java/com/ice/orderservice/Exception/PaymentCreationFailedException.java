package com.ice.orderservice.Exception;

public class PaymentCreationFailedException extends RuntimeException {
    public PaymentCreationFailedException(String message) {
        super(message);
    }
}
