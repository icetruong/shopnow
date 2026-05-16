package com.ice.productservice.Exception;

import com.ice.productservice.DTO.Response.Common.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(IllegalArgumentException ex)
    {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), "INVALID_REQUEST"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {

        // Lấy message của field đầu tiên bị lỗi
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(message, "INVALID_REQUEST"));
    }

    @ExceptionHandler(CategoryHasProductException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryHasProduct(CategoryHasProductException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "CATEGORY_HAS_PRODUCTS"));
    }

    @ExceptionHandler(CategoryHasCategoryChildrenException.class)
    public ResponseEntity<ApiResponse<Void>> handleCategoryHasCategoryChildren(CategoryHasCategoryChildrenException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "CATEGORY_HAS_CHILDREN"));
    }

    @ExceptionHandler(VariantInActiveOrderException.class)
    public ResponseEntity<ApiResponse<Void>> handleVariantInActiveOrder(VariantInActiveOrderException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "VARIANT_IN_ACTIVE_ORDER"));
    }

    @ExceptionHandler(TooManyImagesException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyImages(TooManyImagesException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), "TOO_MANY_IMAGES"));
    }

    @ExceptionHandler(InvalidFileTypeException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidFileType(InvalidFileTypeException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), "INVALID_FILE_TYPE"));
    }

}
