package com.ice.promotionservice.Controller;

import com.ice.promotionservice.DTO.Request.Coupon.ValidationCouponRequest;
import com.ice.promotionservice.DTO.Response.Common.ApiResponse;
import com.ice.promotionservice.DTO.Response.Coupon.ValidationCouponResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/coupons")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<ValidationCouponResponse>> validationCoupon(@Valid @RequestBody ValidationCouponRequest request)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Mã giảm giá hợp lệ",
                        promotionService.validationCoupon(request)
                )
        );
    }
}
