package com.ice.shippingservice.Exception;

public class CarrierCannotCancelException extends RuntimeException {
    public CarrierCannotCancelException(String message) {
        super(message);
    }
}
