package com.ice.shippingservice.DTO.Request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body (tùy chọn) cho POST /internal/shipments/{shipmentId}/cancel.
 * reason chỉ để ghi vào timeline ("Đơn hàng đã bị hủy (...)"); bỏ trống -> dùng mặc định.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CancelShipmentRequest {
    private String reason;
}
