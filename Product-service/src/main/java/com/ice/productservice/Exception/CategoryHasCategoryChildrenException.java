package com.ice.productservice.Exception;

public class CategoryHasCategoryChildrenException extends RuntimeException {
    public CategoryHasCategoryChildrenException(String message) {
        super(message);
    }
}
