package com.ice.inventoryservice.DTO.Response.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemReserveResponseFail {
    private String variantId;
    private String sku;
    private Integer requested;
    private Integer available;
}
