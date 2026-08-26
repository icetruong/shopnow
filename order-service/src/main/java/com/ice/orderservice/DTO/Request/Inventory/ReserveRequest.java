package com.ice.orderservice.DTO.Request.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReserveRequest {
    private String orderId;
    private List<ItemReserveRequest> items;
}
