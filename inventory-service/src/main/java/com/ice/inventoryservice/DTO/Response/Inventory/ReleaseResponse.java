package com.ice.inventoryservice.DTO.Response.Inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseResponse {
    private Boolean success;
    private String orderId;
    private Instant releasedAt;
}
