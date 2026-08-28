package com.ice.shippingservice.DTO.Response.Shipping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ShipmentReprintLabelResponse {
    private String shippingLabel;
}
