package com.ice.orderservice.Exception;

import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.Enum.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice

public class HandlerGlobalException {
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

    @ExceptionHandler(CheckoutTokenExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleCheckoutTokenExpired(CheckoutTokenExpiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.CHECKOUT_TOKEN_EXPIRED.toString()));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleServiceUnavailable(ServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.SERVICE_UNAVAILABLE.toString()));
    }

    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderAccessDenied(OrderAccessDeniedException ex)
    {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ORDER_ACCESS_DENIED.toString()));
    }

    @ExceptionHandler(OrderCannotCancelException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderCannotCancel(OrderCannotCancelException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ORDER_CANNOT_CANCEL.toString()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidStatusTransition(InvalidStatusTransitionException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.INVALID_STATUS_TRANSITION.toString()));
    }

    @ExceptionHandler(PaymentCreationFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentCreateFail(PaymentCreationFailedException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PAYMENT_CREATION_FAILED.toString()));
    }

    @ExceptionHandler(PaymentRefundFailException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentRefundFail(PaymentRefundFailException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PAYMENT_REFUND_FAILED.toString()));
    }

    @ExceptionHandler(PaymentLookupFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentLookupFail(PaymentLookupFailedException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PAYMENT_LOOKUP_FAILED.toString()));
    }

    @ExceptionHandler(PaymentConfirmCodFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handlePaymentConfirmCodFail(PaymentConfirmCodFailedException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PAYMENT_CONFIRM_COD_FAILED.toString()));
    }
}
