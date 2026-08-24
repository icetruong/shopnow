package com.ice.orderservice.Controller.Internal;

import com.ice.orderservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.orderservice.Service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/orders")
@RequiredArgsConstructor
public class OrderInternalController {

    private final OrderService orderService;

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDetailResponse> getOrderDetailInternal(@PathVariable String orderId)
    {
        return ResponseEntity.ok(
                orderService.getOrderDetailInternal(orderId)
        );
    }
}
