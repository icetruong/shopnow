package com.ice.orderservice.Controller.Internal;

import com.ice.orderservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping
    public ResponseEntity<List<OrderDetailResponse>> getOrderOfUser(
            @RequestParam String userId,
            @RequestParam(required = false) List<OrderStatus> statuses
    )
    {
        return ResponseEntity.ok(
                orderService.getOrderOfUser(userId, statuses)
        );
    }
}
