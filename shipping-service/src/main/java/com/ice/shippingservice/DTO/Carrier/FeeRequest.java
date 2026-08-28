package com.ice.shippingservice.DTO.Carrier;

public record FeeRequest(
        int fromDistrictId,
        String fromWardCode,
        int toDistrictId,
        String toWardCode,
        String toProvinceName,
        String toDistrictName,
        String toWardName, // GHTK dùng tên
        int weightGram,
        int lengthCm,
        int widthCm,
        int heightCm,
        long insuranceValue
) {
}
