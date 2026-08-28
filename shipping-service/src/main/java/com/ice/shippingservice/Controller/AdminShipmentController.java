package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Response.Shipping.AdminShipmentPageResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShipmentCreateResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShipmentReprintLabelResponse;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Endpoint admin (ROLE_ADMIN qua SecurityConfig) cho các thao tác tay trên vận đơn.
 */
@RestController
@RequestMapping("/api/v1/admin/shipments")
@RequiredArgsConstructor
public class AdminShipmentController {

    private final ShippingService shippingService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminShipmentPageResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) String carrier,
            @RequestParam(required = false) String failureReason,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách vận đơn thành công",
                        shippingService.getShipmentPageAdmin(
                                page, size, status, carrier, failureReason,
                                keyword, orderId, userId, startDate, endDate)));
    }

    @PostMapping("/{shipmentId}/retry")
    public ResponseEntity<ApiResponse<ShipmentCreateResponse>> retry(@PathVariable String shipmentId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã tạo lại vận đơn",
                        ShipmentCreateResponse.from(shippingService.retry(shipmentId))));
    }

    @PostMapping("/{shipmentId}/reprint-label")
    public ResponseEntity<ApiResponse<ShipmentReprintLabelResponse>> reprintLabel(@PathVariable String shipmentId) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã cấp lại nhãn vận đơn",
                        shippingService.reprintLabel(shipmentId)));
    }
}
