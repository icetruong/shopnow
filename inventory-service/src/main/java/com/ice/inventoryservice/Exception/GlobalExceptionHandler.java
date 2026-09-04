package com.ice.inventoryservice.Exception;

import com.ice.inventoryservice.DTO.Response.Common.ApiResponse;
import com.ice.inventoryservice.DTO.Response.Inventory.ReserveResponseFail;
import com.ice.inventoryservice.Exception.FlashSaleUserLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
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
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex)
    {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode().toString()));
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

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ReserveResponseFail> handleInsufficientStock(InsufficientStockException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ReserveResponseFail(
                        false,
                        ex.getInsufficientCode(),
                        ex.getMessage(),
                        ex.getItem()
                ));
    }

    @ExceptionHandler(FlashSaleSoldOutException.class)
    public ResponseEntity<ApiResponse<Void>> handleFlashSaleSoldOut(FlashSaleSoldOutException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "FLASH_SALE_SOLD_OUT"));
    }

    @ExceptionHandler(FlashSaleUserLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleFlashSaleUserLimit(FlashSaleUserLimitException ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "FLASH_SALE_USER_LIMIT"));
    }

    @ExceptionHandler(NotEnoughToUserForFlashSale.class)
    public ResponseEntity<ApiResponse<Void>> handleNotEnoughToUserForFlashSale(NotEnoughToUserForFlashSale ex)
    {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "NOT_ENOUGH"));
    }

    @ExceptionHandler(InventoryExistException.class)
    public ResponseEntity<ApiResponse<List<String>>> handleInventoryExist(InventoryExistException ex)
    {
        if(ex.variantIds.isEmpty())
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(ex.getMessage(), "INVENTORY_ALREADY_EXISTS"));
        else
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ApiResponse<>(false,ex.getMessage(),ex.variantIds,"INVENTORY_ALREADY_EXISTS"));
    }

    @ExceptionHandler(FlashSaleNotActiveException.class)
    public ResponseEntity<ApiResponse<Void>> handleFlashSaleNotActive(FlashSaleNotActiveException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), "FLASH_SALE_NOT_ACTIVE"));
    }

    @ExceptionHandler(FlashSaleAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleFlashSaleAlreadyExists(FlashSaleAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ex.getMessage(), "FLASH_SALE_ALREADY_EXISTS"));
    }
}
