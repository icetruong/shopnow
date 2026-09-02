package com.ice.reviewservice.Exception;

public class PurchaseRequiredException extends RuntimeException {
    public PurchaseRequiredException(String message) {
        super(message);
    }
}
