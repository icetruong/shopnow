package com.ice.shippingservice.Controller;

import com.ice.shippingservice.DTO.Location.DistrictResponse;
import com.ice.shippingservice.DTO.Location.ProvinceResponse;
import com.ice.shippingservice.DTO.Location.WardResponse;
import com.ice.shippingservice.DTO.Request.ShippingFeeRequest;
import com.ice.shippingservice.DTO.Response.Common.ApiResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShipmentResponse;
import com.ice.shippingservice.DTO.Response.Shipping.ShippingFeeResponse;
import com.ice.shippingservice.Service.LocationService;
import com.ice.shippingservice.Service.ShippingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ShippingController {

    private final ShippingService shippingService;
    private final LocationService locationService;

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

    @GetMapping("/shipping/provinces")
    public ResponseEntity<ApiResponse<List<ProvinceResponse>>> provinces()
    {
        return ResponseEntity.ok(
                ApiResponse.success("OK", locationService.getProvinces())
        );
    }

    @GetMapping("/shipping/districts")
    public ResponseEntity<ApiResponse<List<DistrictResponse>>> districts(@RequestParam Integer provinceId)
    {
        return ResponseEntity.ok(
                ApiResponse.success("OK", locationService.getDistricts(provinceId))
        );
    }

    @GetMapping("/shipping/wards")
    public ResponseEntity<ApiResponse<List<WardResponse>>> wards(@RequestParam Integer districtId)
    {
        return ResponseEntity.ok(
                ApiResponse.success("OK", locationService.getWards(districtId))
        );
    }

    @GetMapping("/shipments/order/{orderId}")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getShipment(@PathVariable String orderId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy thông tin vận đơn thành công",
                        shippingService.getShipment(orderId, userId)
                )
        );
    }
}
