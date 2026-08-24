package com.ice.orderservice.Exception;

public class OrderCannotCancelException extends RuntimeException {
    public OrderCannotCancelException(String message) {
        super(message);
    }
}
