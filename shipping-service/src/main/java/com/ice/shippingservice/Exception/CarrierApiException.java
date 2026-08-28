package com.ice.shippingservice.Exception;

public class CarrierApiException extends RuntimeException {
    public CarrierApiException(String message) {
        super(message);
    }
    public CarrierApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
