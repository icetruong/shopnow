package com.ice.shippingservice.DTO.Event.Publish;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentUpdatePayload {
    private String orderId;
    private String shipmentId;
    private String trackingCode;
    private String carrier;
    private String status;
    private String description;
    private Instant estimatedDate;
}
