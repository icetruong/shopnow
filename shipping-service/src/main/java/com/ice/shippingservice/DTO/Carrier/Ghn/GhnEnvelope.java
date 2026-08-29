package com.ice.shippingservice.DTO.Carrier.Ghn;

/**
 * Vỏ chung mọi response GHN v2: {@code {"code":200,"message":"Success","data":{...}}}.
 * VERIFY: đối chiếu tài liệu GHN hiện hành khi có token (mã code/thông điệp lỗi).
 */
public record GhnEnvelope<T>(int code, String message, T data) {
}
