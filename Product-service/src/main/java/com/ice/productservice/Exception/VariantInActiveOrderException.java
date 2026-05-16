package com.ice.productservice.Exception;

public class VariantInActiveOrderException extends RuntimeException {
    public VariantInActiveOrderException(String message) {
        super(message);
    }
}
