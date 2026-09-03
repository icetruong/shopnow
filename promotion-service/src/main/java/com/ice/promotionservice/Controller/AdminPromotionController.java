package com.ice.promotionservice.Controller;

import com.ice.promotionservice.DTO.Request.Coupon.AdminCreateRequest;
import com.ice.promotionservice.DTO.Request.Coupon.CouponUpdateRequest;
import com.ice.promotionservice.DTO.Response.Common.ApiResponse;
import com.ice.promotionservice.DTO.Response.Coupon.AdminCreateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUpdateResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @PutMapping("/{couponId}")
    public ResponseEntity<ApiResponse<CouponUpdateResponse>> updateCoupon(
            @PathVariable UUID couponId,
            @Valid @RequestBody CouponUpdateRequest request)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Cập nhật coupon thành công",
                        promotionService.updateCoupon(couponId, request)
                )
        );
    }

    @DeleteMapping("/{couponId}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable UUID couponId)
    {
        promotionService.deleteCoupon(couponId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã vô hiệu hóa coupon",
                        null
                )
        );
    }
}
