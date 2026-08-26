package com.ice.orderservice.DTO.Request.Inventory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ItemReserveRequest {
    private String variantId;
    private Integer qty;
}
