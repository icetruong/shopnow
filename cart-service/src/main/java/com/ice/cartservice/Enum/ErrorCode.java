package com.ice.cartservice.Enum;

public enum ErrorCode {
    OUT_OF_STOCK, // Sản phẩm hết hàng
    INSUFFICIENT_STOCK, // Không đủ số lượng yêu cầu
    CART_ITEM_NOT_FOUND, // CartItemId không tồn tại trong giỏ
    CART_EMPTY, // Giỏ hàng rỗng khi checkout
    CART_VALIDATION_FAILED, // Có item hết hàng / giá thay đổi
    CHECKOUT_TOKEN_EXPIRED, // Token checkout hết hạn (15 phút)
    PRODUCT_UNAVAILABLE, // Sản phẩm đã bị ẩn / ngừng bán
    MAX_QTY_EXCEEDED // qty vượt quá 99
}
