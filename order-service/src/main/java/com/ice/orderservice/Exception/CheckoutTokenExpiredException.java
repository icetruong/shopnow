package com.ice.orderservice.Exception;

public class CheckoutTokenExpiredException extends RuntimeException {
    public CheckoutTokenExpiredException(String message) {
        super(message);
    }
}
