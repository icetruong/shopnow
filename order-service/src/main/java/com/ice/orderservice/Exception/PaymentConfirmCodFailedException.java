package com.ice.orderservice.Exception;

public class PaymentConfirmCodFailedException extends RuntimeException {
    public PaymentConfirmCodFailedException(String message) {
        super(message);
    }
}
