package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Request.GetInventoryByVariantRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.GetInventoryByVariantResponse;
import com.ice.inventoryservice.DTO.Response.Inventory.InventoryResponse;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Enum.StockStatus;
import com.ice.inventoryservice.Exception.ResourceNotFoundException;
import com.ice.inventoryservice.Repository.InventoryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepo inventoryRepo;

    public InventoryResponse getInventory(UUID variantId)
    {
        Inventory inventory = inventoryRepo.findByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found", ErrorCode.INVENTORY_NOT_FOUND));


        return toInventoryResponse(inventory);
    }

    public GetInventoryByVariantResponse getByVariants(GetInventoryByVariantRequest request)
    {
        List<Inventory> inventories = inventoryRepo.findAllByVariantIdIn(request.getVariantIds().stream().map(UUID::fromString).toList());

        return new GetInventoryByVariantResponse(inventories.stream().map(
                this::toInventoryResponse
        ).toList());
    }

    private InventoryResponse toInventoryResponse(Inventory inventory)
    {
        return new InventoryResponse(
            inventory.getVariantId().toString(),
            inventory.getSku(),
            inventory.getStockQty(),
            inventory.getReservedQty(),
            inventory.getAvailableQty(),
            inventory.getAvailableQty() > 10 ? StockStatus.IN_STOCK : inventory.getAvailableQty() > 0 ? StockStatus.LOW_STOCK : StockStatus.OUT_OF_STOCK
        );
    }


}
