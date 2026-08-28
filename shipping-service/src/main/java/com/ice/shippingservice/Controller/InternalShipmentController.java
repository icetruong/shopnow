package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Request.CancelShipmentRequest;
import com.ice.shippingservice.DTO.Request.CreateShipmentRequest;
import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShipmentCreateResponse;
import com.ice.shippingservice.Entity.Shipment;
import com.ice.shippingservice.Enum.ShipmentStatus;
import com.ice.shippingservice.Exception.AddressMappingFailedException;
import com.ice.shippingservice.Service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Endpoint nội bộ (X-Internal-Token qua InternalTokenFilter) - dùng bởi retry job / admin
 * để tạo lại vận đơn khi luồng Kafka order.confirmed fail.
 */
@RestController
@RequestMapping("/api/v1/internal/shipments")
@RequiredArgsConstructor
public class InternalShipmentController {

    private final ShippingService shippingService;

    @PostMapping
    public ResponseEntity<ApiResponse<ShipmentCreateResponse>> create(
            @Valid @RequestBody CreateShipmentRequest request) {

        ShippingService.ShipmentCreationResult result =
                shippingService.createForOrder(request.getOrderId(), UUID.fromString(request.getUserId()));

        Shipment shipment = result.shipment();

        // 422: map địa chỉ fail - shipment vẫn được INSERT dạng PENDING (transaction đã commit)
        if (shipment.getStatus() == ShipmentStatus.PENDING
                && "ADDRESS_MAPPING_FAILED".equals(shipment.getFailureReason())) {
            throw new AddressMappingFailedException(
                    "Không xác định được mã quận/phường cho địa chỉ giao hàng.");
        }

        HttpStatus code = result.alreadyExisted() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(code).body(
                ApiResponse.success(
                        result.alreadyExisted() ? "Vận đơn đã tồn tại" : "Tạo vận đơn thành công",
                        ShipmentCreateResponse.from(shipment)));
    }

    /**
     * Hủy vận đơn theo shipmentId - gọi bởi admin UI / service khác (không qua Kafka).
     * 200 nếu hủy được hoặc đã CANCELLED sẵn (idempotent); 409 SHIPMENT_CANNOT_CANCEL nếu đã lấy hàng.
     */
    @PostMapping("/{shipmentId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable String shipmentId,
            @RequestBody(required = false) CancelShipmentRequest request) {

        String reason = request != null ? request.getReason() : null;
        shippingService.cancelByShipmentId(shipmentId, reason);

        return ResponseEntity.ok(ApiResponse.<Void>success("Đã hủy vận đơn.", null));
    }
}
