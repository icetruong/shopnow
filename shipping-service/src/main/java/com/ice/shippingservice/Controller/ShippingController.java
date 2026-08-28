package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Request.ShippingFeeRequest;
import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShippingFeeResponse;
import com.ice.shippingservice.Service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ShippingController {

    private final ShippingService shippingService;

    @PostMapping("/shipping/calculate-fee")
    public ResponseEntity<ApiResponse<List<ShippingFeeResponse>>> calculateFee(@Valid @RequestBody ShippingFeeRequest request)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Tính phí vận chuyển thành công",
                        shippingService.calculateFee(request)
                )
        );
    }
}
