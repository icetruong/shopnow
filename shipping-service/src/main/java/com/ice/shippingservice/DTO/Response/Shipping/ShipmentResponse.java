package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentResponse {
    private String shipmentId;
    private String orderId;
    private String carrier;
    private String trackingCode;
    private String status;
    private LocalDate estimatedDate;
    private List<ShipmentTimelineResponse> timeline;
}
