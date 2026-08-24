package com.ice.orderservice.Controller;

import com.ice.orderservice.DTO.Request.Order.CreatedOrderRequest;
import com.ice.orderservice.DTO.Response.Common.ApiResponse;
import com.ice.orderservice.DTO.Response.Order.CreatedOrderResponse;
import com.ice.orderservice.Service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
