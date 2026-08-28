package com.ice.shippingservice.DTO.Response.Shipping;

import com.ice.shippingservice.Entity.Shipment;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentCreateResponse {
    private String shipmentId;
    private String orderId;
    private String carrier;
    private String trackingCode;
    private String status;
    private LocalDate estimatedDate;
    private String shippingLabel;

    public static ShipmentCreateResponse from(Shipment s) {
        return new ShipmentCreateResponse(
                s.getId().toString(), s.getOrderId().toString(), s.getCarrier(),
                s.getTrackingCode(), s.getStatus().name(),
                s.getEstimatedDate(), s.getShippingLabelUrl());
    }
}
