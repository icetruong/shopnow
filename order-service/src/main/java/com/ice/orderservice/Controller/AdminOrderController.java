package com.ice.orderservice.Controller;

import com.ice.orderservice.DTO.Request.Order.AdminUpdateStatusOrderRequest;
import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.DTO.Response.Order.AdminOrderPageResponse;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.PaymentStatus;
import com.ice.orderservice.Service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {
    private final OrderService orderService;

    @GetMapping
    public ResponseEntity<ApiResponse<AdminOrderPageResponse>> getOrderPageAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách đơn hàng thành công",
                        orderService.getOrderPageAdmin(page, size, status, paymentStatus, keyword, userId, startDate, endDate)
                )
        );
    }

    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<Void>> updateStatusOrder(@Valid @RequestBody AdminUpdateStatusOrderRequest request, @PathVariable String orderId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        orderService.updateStatusOrder(request, orderId, userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã cập nhật trạng thái đơn hàng.",
                        null
                )
        );
    }
}
