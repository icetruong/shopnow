package com.ice.cartservice.Enum;

public enum StockStatus {
    IN_STOCK, // availableQty > 10
    LOW_STOCK, // availableQty 1-10
    OUT_OF_STOCK // availableQty = 0
}
