package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentTimelineResponse {
    private String status;
    private String description;
    private String location;
    private LocalDateTime at;
}
