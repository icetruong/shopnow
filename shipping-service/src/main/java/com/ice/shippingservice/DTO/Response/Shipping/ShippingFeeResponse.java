package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShippingFeeResponse {
    private String carrier;
    private String serviceId;
    private String serviceName;
    private Long fee;
    private Integer estimatedDays;
    private LocalDate estimatedDate;
}
