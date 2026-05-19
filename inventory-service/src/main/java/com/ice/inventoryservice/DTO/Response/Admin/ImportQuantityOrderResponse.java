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
public class ImportQuantityOrderResponse {
    private String variantId;
    private Integer previousQty;
    private Integer importedQty;
    private Integer currentQty;
    private Instant updatedAt;
}
