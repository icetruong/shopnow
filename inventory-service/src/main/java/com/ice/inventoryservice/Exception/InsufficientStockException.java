package com.ice.inventoryservice.Exception;

import com.ice.inventoryservice.DTO.Response.Inventory.ItemReserveResponseFail;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
public class InsufficientStockException extends RuntimeException {
    private final String insufficientCode;
    private final List<ItemReserveResponseFail> item;
    public InsufficientStockException(String message, String errorCode, List<ItemReserveResponseFail> item) {
        super(message);
        this.insufficientCode = errorCode;
        this.item = item;
    }
}
