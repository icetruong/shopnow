package com.ice.orderservice.Controller;

import com.ice.orderservice.DTO.Request.Order.CreatedOrderRequest;
import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.DTO.Response.Order.CreatedOrderResponse;
import com.ice.orderservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.orderservice.DTO.Response.Order.OrderPageResponse;
import com.ice.orderservice.Enum.OrderStatus;
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
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreatedOrderResponse>> createOrder(@Valid @RequestBody CreatedOrderRequest request, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(ApiResponse.success(
                "Đặt hàng thành công",
                orderService.createOrder(request, userId)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OrderPageResponse>> getOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
            )
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy danh sách đơn hàng thành công",
                orderService.getOrders(page, size, status, startDate, endDate, userId)
        ));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> getOrderDetail(@PathVariable String orderId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(ApiResponse.success(
                "Lấy chi tiết đơn hàng thành công",
                orderService.getOrderDetail(orderId, userId)
        ));
    }
}
