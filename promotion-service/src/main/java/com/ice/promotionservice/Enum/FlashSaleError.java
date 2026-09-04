package com.ice.promotionservice.Enum;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ ở các endpoint flash sale (purchase / rollback / admin / warmup). */
public enum FlashSaleError {
    FLASH_SALE_NOT_FOUND(HttpStatus.NOT_FOUND, "Flash sale không tồn tại."),
    FLASH_SALE_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Sản phẩm không nằm trong flash sale này."),
    FLASH_SALE_SOLD_OUT(HttpStatus.CONFLICT, "Sản phẩm flash sale đã hết!"),
    FLASH_SALE_LIMIT_REACHED(HttpStatus.CONFLICT, "Bạn đã mua đủ giới hạn trong flash sale này."),
    FLASH_SALE_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "Flash sale chưa bắt đầu hoặc đã kết thúc."),

    // Admin tạo flash sale
    FLASH_SALE_TIME_RANGE_INVALID(HttpStatus.BAD_REQUEST, "endsAt phải sau startsAt và sau thời điểm hiện tại."),
    FLASH_SALE_DUPLICATE_VARIANT(HttpStatus.BAD_REQUEST, "Danh sách items có variantId trùng nhau."),

    // Admin warmup flash sale
    FLASH_SALE_NO_ITEMS(HttpStatus.BAD_REQUEST, "Flash sale chưa có sản phẩm nào để warmup."),
    FLASH_SALE_ALREADY_WARMED(HttpStatus.CONFLICT, "Flash sale này đã được nạp tồn kho rồi."),
    FLASH_SALE_WARMUP_INVENTORY_MISSING(HttpStatus.BAD_REQUEST, "Một số variant chưa có tồn kho, không thể warmup."),
    FLASH_SALE_WARMUP_NOT_ENOUGH_STOCK(HttpStatus.CONFLICT, "Kho không đủ để mở flash sale.");

    private final HttpStatus status;
    private final String message;

    FlashSaleError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
