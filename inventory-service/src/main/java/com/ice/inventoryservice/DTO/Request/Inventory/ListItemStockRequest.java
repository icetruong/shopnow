package com.ice.inventoryservice.DTO.Request.Inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ListItemStockRequest {
    @NotNull
    @NotEmpty(message = "items must not empty")
    @Valid
    List<InsertInventoryRequest> items;
}
