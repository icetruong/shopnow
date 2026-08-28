package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShipmentCreateResponse;
import com.ice.shippingservice.Service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint admin (ROLE_ADMIN qua SecurityConfig) cho các thao tác tay trên vận đơn.
 */
@RestController
@RequestMapping("/api/v1/admin/shipments")
@RequiredArgsConstructor
public class AdminShipmentController {

    private final ShippingService shippingService;

    @PostMapping("/{shipmentId}/retry")
    public ResponseEntity<ApiResponse<ShipmentCreateResponse>> retry(@PathVariable String shipmentId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã tạo lại vận đơn",
                        ShipmentCreateResponse.from(shippingService.retry(shipmentId))));
    }
}
