package com.ice.notificationservice.Exception;

import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.Enum.ErrorCode;
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

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotificationNotFound(NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.NOTIFICATION_NOT_FOUND.toString()));
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleTemplateNotFound(TemplateNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.TEMPLATE_NOT_FOUND.toString()));
    }

    @ExceptionHandler(DeviceTokenInvalidException.class)
    public ResponseEntity<ApiResponse<Void>> handleDeviceTokenInvalid(DeviceTokenInvalidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.DEVICE_TOKEN_INVALID.toString()));
    }

    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ApiResponse<Void>> handleProviderError(ProviderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.PROVIDER_ERROR.toString()));
    }
}
