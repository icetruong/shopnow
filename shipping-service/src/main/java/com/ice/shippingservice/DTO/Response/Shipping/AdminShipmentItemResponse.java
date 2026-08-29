package com.ice.shippingservice.DTO.Response.Shipping;

import com.ice.shippingservice.Entity.Shipment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** 1 dòng trong danh sách GET /admin/shipments. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminShipmentItemResponse {
    private String shipmentId;
    private String orderId;
    private String orderCode;
    private String userId;
    private String carrier;
    private String trackingCode;
    private String status;
    private String toName;
    private String toProvince;
    private Long shippingFee;
    private Long codAmount;
    private LocalDate estimatedDate;
    private String failureReason;
    private Instant createdAt;

    public static AdminShipmentItemResponse from(Shipment s) {
        return new AdminShipmentItemResponse(
                s.getId().toString(),
                s.getOrderId().toString(),
                s.getOrderCode(),
                s.getUserId().toString(),
                s.getCarrier(),
                s.getTrackingCode(),
                s.getStatus().name(),
                s.getToName(),
                s.getToProvince(),
                s.getShippingFee(),
                s.getCodAmount(),
                s.getEstimatedDate(),
                s.getFailureReason(),
                s.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
