package com.ice.shippingservice.DTO.Carrier;

public record Recipient(
        String name,
        String phone,
        String address,        // = streetDetail
        String provinceName,
        String districtName,
        String wardName,
        Integer districtId,
        String wardCode
) {
}
