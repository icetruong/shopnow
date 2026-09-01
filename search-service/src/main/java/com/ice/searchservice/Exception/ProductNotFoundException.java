package com.ice.searchservice.Exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productId) {
        super("Sản phẩm không tồn tại: " + productId);
    }
}
