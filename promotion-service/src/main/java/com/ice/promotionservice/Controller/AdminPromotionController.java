package com.ice.promotionservice.Controller;

import com.ice.promotionservice.DTO.Request.Coupon.AdminCreateRequest;
import com.ice.promotionservice.DTO.Response.Common.ApiResponse;
import com.ice.promotionservice.DTO.Response.Coupon.AdminCreateResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/coupons")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<ApiResponse<AdminCreateResponse>> createCoupon(@Valid @RequestBody AdminCreateRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Tạo mã giảm giá thành công",
                        promotionService.createCoupon(request)
                )
        );
    }
}
