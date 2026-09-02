package com.ice.reviewservice.Exception;

import com.ice.reviewservice.DTO.Response.Common.ApiResponse;
import com.ice.reviewservice.Enum.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class HandleGlobalException {
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

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {

        String message = ex.getConstraintViolations()
                .stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return field + ": " + violation.getMessage();
                })
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(message, "INVALID_REQUEST"));
    }

    @ExceptionHandler(OrderServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderServiceUnavailable(OrderServiceUnavailableException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ORDER_SERVICE_UNAVAILABLE.name()));
    }

    @ExceptionHandler(OrderNotDeliveredException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderNotDelivered(OrderNotDeliveredException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ORDER_NOT_DELIVERED.name()));
    }

    @ExceptionHandler(AlreadyReviewedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAlreadyReviewed(AlreadyReviewedException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ALREADY_REVIEWED.name()));
    }

    @ExceptionHandler(PurchaseRequiredException.class)
    public ResponseEntity<ApiResponse<Void>> handlePurchaseRequired(PurchaseRequiredException ex)
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PURCHASE_REQUIRED.name()));
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUserServiceUnavailable(UserServiceUnavailableException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.USER_SERVICE_UNAVAILABLE.name()));
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleReviewNotFound(ReviewNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.REVIEW_NOT_FOUND.name()));
    }

    @ExceptionHandler(EditWindowExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleEditWindowExpired(EditWindowExpiredException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.EDIT_WINDOW_EXPIRED.name()));
    }
}
