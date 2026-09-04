package com.ice.inventoryservice.Controller.Internal;

import com.ice.inventoryservice.DTO.Request.Admin.FlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.*;
import com.ice.inventoryservice.DTO.Response.Common.ApiResponse;
import com.ice.inventoryservice.DTO.Response.Inventory.*;
import com.ice.inventoryservice.Service.FlashSaleService;
import com.ice.inventoryservice.Service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class InternalInventoryController {

    private final InventoryService inventoryService;
    private final FlashSaleService flashSaleService;

    @GetMapping("/stock/{variantId}")
    public ResponseEntity<InventoryResponse> getStock(@PathVariable UUID variantId)
    {
        return ResponseEntity.ok(
                inventoryService.getInventory(variantId)
        );
    }

    @PostMapping("/stock/batch")
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

    @PostMapping("/stock/release")
    public ResponseEntity<ReleaseResponse> release(@Valid @RequestBody ReleaseRequest request)
    {
        return ResponseEntity.ok(
                inventoryService.releaseOrder(request)
        );
    }

    @PostMapping("/stock/deduct")
    public ResponseEntity<DeductResponse> deduct(@Valid @RequestBody DeductRequest request)
    {
        return ResponseEntity.ok(
                inventoryService.deductOrder(request)
        );
    }

    @PostMapping("/stock/return")
    public ResponseEntity<ReturnResponse> returnOrder(@Valid @RequestBody ReturnRequest request)
    {
        return ResponseEntity.ok(
                inventoryService.returnOrder(request)
        );
    }

    @PostMapping("/stock/flash-sale/reserve")
    public ResponseEntity<FlashSaleReserveResponse> flashSaleReserve(@Valid @RequestBody FlashSaleReserveRequest request) {
        return ResponseEntity.ok(flashSaleService.reserve(request));
    }

    @PostMapping("/stock/flash-sale/release")
    public ResponseEntity<Void> flashSaleRelease(@Valid @RequestBody FlashSaleReleaseRequest request) {
        flashSaleService.release(request);
        return ResponseEntity.ok(null);
    }

    @PostMapping("/flash-sale-stock")
    public ResponseEntity<ApiResponse<Void>> warmupFlashSale(@Valid @RequestBody FlashSaleRequest request) {
        flashSaleService.createdFlashSale(request);
        return ResponseEntity.ok(
                ApiResponse.success("Đã khởi tạo tồn kho flash sale thành công.", null)
        );
    }

    @PostMapping("/flash-sale-stock/{flashSaleId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateFlashSale(@PathVariable UUID flashSaleId) {
        flashSaleService.activate(flashSaleId);
        return ResponseEntity.ok(
                ApiResponse.success("Đã kích hoạt flash sale.", null)
        );
    }


    @PostMapping("/stock")
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InsertInventoryRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.addInventory(request));
    }

    @PostMapping("/stock/bulk")
    public ResponseEntity<ListInventoryResponse> createAllInventory(@Valid @RequestBody ListItemStockRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inventoryService.addAllInventory(request));
    }

}
