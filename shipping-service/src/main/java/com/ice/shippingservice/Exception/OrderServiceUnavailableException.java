package com.ice.shippingservice.Exception;

public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message) {
        super(message);
    }
}
