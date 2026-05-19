package com.ice.inventoryservice.DTO.Response.Admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdjustQuantityOrderResponse {
    private String variantId;
    private Integer previousQty;
    private Integer adjustedQty;
    private Integer difference;
    private Instant updatedAt;
}
