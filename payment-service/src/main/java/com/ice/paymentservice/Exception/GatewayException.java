package com.ice.paymentservice.Exception;

public class GatewayException extends RuntimeException {
    public GatewayException(String message) {
        super(message);
    }
}
