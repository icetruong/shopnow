package com.ice.shippingservice.DTO.Carrier;

import java.util.List;

public record CreateOrderRequest(
        String orderCode,
        String serviceId,
        Recipient to,                 // name, phone, address, provinceName, districtName, wardName,
        // districtId (GHN), wardCode (GHN)
        int weightGram, int lengthCm, int widthCm, int heightCm,
        long codAmount,
        long insuranceValue,
        String note,
        List<ItemLine> items
) {
    public CreateOrderRequest {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
