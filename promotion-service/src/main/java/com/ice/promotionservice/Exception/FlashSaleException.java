package com.ice.promotionservice.Exception;

import com.ice.promotionservice.Enum.FlashSaleError;
import lombok.Getter;

@Getter
public class FlashSaleException extends RuntimeException {
    private final FlashSaleError error;

    public FlashSaleException(FlashSaleError error) {
        super(error.getMessage());
        this.error = error;
    }
}
