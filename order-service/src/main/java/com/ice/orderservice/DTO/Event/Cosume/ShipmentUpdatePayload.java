package com.ice.orderservice.DTO.Event.Cosume;

import com.ice.orderservice.Enum.ShipmentStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentUpdatePayload {
    private String orderId;
    private String shipmentId;
    private String trackingCode;
    private String carrier;
    private ShipmentStatus status;
    private String description;
    private LocalDate estimatedDate;
}
