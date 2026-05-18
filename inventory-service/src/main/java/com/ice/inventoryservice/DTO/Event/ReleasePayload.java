package com.ice.inventoryservice.DTO.Event;

import com.ice.inventoryservice.DTO.Request.Inventory.ItemReserveRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleasePayload {
    private String orderId;
    private String reason;
    private Instant releasedAt;
    private List<ItemReserveRequest> items;
}
