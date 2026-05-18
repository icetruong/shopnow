package com.ice.inventoryservice.Controller;

import com.ice.inventoryservice.DTO.Response.Admin.PageStockResponse;
import com.ice.inventoryservice.DTO.Response.Common.ApiResponse;
import com.ice.inventoryservice.Enum.StockStatus;
import com.ice.inventoryservice.Service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class InventoryController {
    private final InventoryService inventoryService;

    @GetMapping("/stock")
    public ResponseEntity<ApiResponse<PageStockResponse>> getStock(
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
}
