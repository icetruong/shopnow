package com.ice.promotionservice.Enum;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ ở các endpoint flash sale (purchase / rollback). */
public enum FlashSaleError {
    FLASH_SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale không tồn tại."),
    FLASH_SALE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Sản phẩm không nằm trong flash sale này."),
    FLASH_SALE_SOLD_OUT(HttpStatus.CONFLICT, "Sản phẩm flash sale đã hết!"),
    FLASH_SALE_LIMIT_REACHED(HttpStatus.CONFLICT, "Bạn đã mua đủ giới hạn trong flash sale này."),
    FLASH_SALE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "Flash sale chưa bắt đầu hoặc đã kết thúc.");

    private final HttpStatus status;
    private final String message;

    FlashSaleError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
