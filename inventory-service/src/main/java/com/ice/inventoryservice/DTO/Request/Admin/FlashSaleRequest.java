package com.ice.inventoryservice.DTO.Request.Admin;

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
public class FlashSaleRequest {
    private String flashSaleId;
    private Instant startsAt;
    private Instant endsAt;
    List<ItemFlashSaleRequest> items;
}
