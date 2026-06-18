package com.ice.cartservice.Exception;

import com.ice.cartservice.Enum.ErrorCode;
import lombok.Getter;

@Getter
public class StockQuantityException extends RuntimeException {
    private final ErrorCode errorCode;
    public StockQuantityException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
}
