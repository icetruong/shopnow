package com.ice.productservice.Exception;

public class CategoryHasProductException extends RuntimeException {
    public CategoryHasProductException(String message) {
        super(message);
    }
}
