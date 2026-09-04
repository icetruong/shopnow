package com.ice.inventoryservice.Controller;

import com.ice.inventoryservice.DTO.Request.Admin.AdjustQuantityOrderRequest;
import com.ice.inventoryservice.DTO.Request.Admin.ImportQuantityOrderRequest;
import com.ice.inventoryservice.DTO.Response.Admin.*;
import com.ice.inventoryservice.DTO.Response.Common.ApiResponse;
import com.ice.inventoryservice.Enum.StockStatus;
import com.ice.inventoryservice.Enum.StockTransactionType;
import com.ice.inventoryservice.Service.InventoryService;
import com.ice.inventoryservice.Service.StockTransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class InventoryController {
    private final InventoryService inventoryService;
    private final StockTransactionService stockTransactionService;

    @GetMapping("/stock")
    public ResponseEntity<ApiResponse<PageStockResponse<StockResponse>>> getStock(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) UUID variantId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) StockStatus status
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách tồn kho thành công.",
                        inventoryService.getStockForAdmin(page,size, variantId, productId, status)
                )
        );
    }

    @PostMapping("/stock/{variantId}/import")
    public ResponseEntity<ApiResponse<ImportQuantityOrderResponse>> importQuantity(@PathVariable UUID variantId,@Valid @RequestBody ImportQuantityOrderRequest request)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Nhập hàng thành công.",
                        inventoryService.importQuantity(variantId, request)
                )
        );
    }

    @PostMapping("/stock/{variantId}/adjust")
    public ResponseEntity<ApiResponse<AdjustQuantityOrderResponse>> adjustQuantity(@PathVariable UUID variantId, @Valid @RequestBody AdjustQuantityOrderRequest request)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Điều chỉnh tồn kho thành công.",
                        inventoryService.adjustQuantity(variantId, request)
                )
        );
    }

    @GetMapping("/stock/{variantId}/history")
    public ResponseEntity<ApiResponse<PageStockResponse<HistoryTransactionResponse>>> getHistoryTransaction(
            @PathVariable UUID variantId,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam(required = false) StockTransactionType type
            )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy lịch sử biến động tồn kho thành công.",
                        stockTransactionService.getHistory(variantId, page, size, startDate, endDate, type)
                )
        );
    }
}
