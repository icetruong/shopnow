package com.ice.shippingservice.Exception;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.Enum.ErrorCode;
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

    @ExceptionHandler(ShipmentNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleShipmentNotFound(ShipmentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.SHIPMENT_NOT_FOUND.name()));
    }

    @ExceptionHandler(ShipmentAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleShipmentAccessDenied(ShipmentAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.SHIPMENT_ACCESS_DENIED.name()));
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

    @ExceptionHandler(CarrierCannotCancelException.class)
    public ResponseEntity<ApiResponse<Void>> handleCarrierCannotCancel(CarrierCannotCancelException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.SHIPMENT_CANNOT_CANCEL.name()));
    }

    @ExceptionHandler(CarrierApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleCarrierApi(CarrierApiException ex)
    {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.CARRIER_API_ERROR.name()));
    }

    @ExceptionHandler(FeeCalculationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFeeCalc(FeeCalculationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.FEE_CALCULATION_FAILED.name()));
    }

    @ExceptionHandler(OrderServiceUnavailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleOrderServiceUnavailable(OrderServiceUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ORDER_SERVICE_UNAVAILABLE.name()));
    }

    @ExceptionHandler(InvalidWebhookException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidWebhook(InvalidWebhookException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.INVALID_WEBHOOK.name()));
    }

    @ExceptionHandler(AddressMappingFailedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAddressMappingFailed(AddressMappingFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail(ex.getMessage(), ErrorCode.ADDRESS_MAPPING_FAILED.name()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "CONFLICT"));
    }
}
