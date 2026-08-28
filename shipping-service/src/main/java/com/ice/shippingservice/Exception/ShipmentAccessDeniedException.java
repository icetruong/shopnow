package com.ice.shippingservice.Exception;

public class ShipmentAccessDeniedException extends RuntimeException {
    public ShipmentAccessDeniedException(String message) {
        super(message);
    }
}
