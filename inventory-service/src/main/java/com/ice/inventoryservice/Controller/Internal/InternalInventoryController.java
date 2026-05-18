package com.ice.inventoryservice.Controller.Internal;

import com.ice.inventoryservice.DTO.Request.Inventory.GetInventoryByVariantRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.ReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.GetInventoryByVariantResponse;
import com.ice.inventoryservice.DTO.Response.Inventory.InventoryResponse;
import com.ice.inventoryservice.DTO.Response.Inventory.ReserveResponseSuccess;
import com.ice.inventoryservice.Service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class InternalInventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/stock/{variantId}")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable UUID variantId)
    {
        return ResponseEntity.ok(
                inventoryService.getInventory(variantId)
        );
    }

    @GetMapping("/stock/batch")
    public ResponseEntity<GetInventoryByVariantResponse> getStockByVariants(@Valid @RequestBody GetInventoryByVariantRequest request)
    {
        return ResponseEntity.ok(
                inventoryService.getByVariants(request)
        );
    }

    @PostMapping("/stock/reserve")
    public ResponseEntity<ReserveResponseSuccess> reserve(@Valid @RequestBody ReserveRequest request)
    {
        return ResponseEntity.ok(
                inventoryService.reserveOrder(request)
        );
    }

}
