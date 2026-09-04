package com.ice.promotionservice.Controller;

import com.ice.promotionservice.DTO.Request.Coupon.AdminCreateRequest;
import com.ice.promotionservice.DTO.Request.Coupon.CouponUpdateRequest;
import com.ice.promotionservice.DTO.Request.FlashSale.CreateAdminFlashSaleRequest;
import com.ice.promotionservice.DTO.Response.Common.ApiResponse;
import com.ice.promotionservice.DTO.Response.Coupon.AdminCreateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUpdateResponse;
import com.ice.promotionservice.DTO.Response.Coupon.PageCouponAdminResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.CreateFlashSaleAdminResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.WarmupFlashSaleResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<AdminCreateResponse>> createCoupon(@Valid @RequestBody AdminCreateRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Tạo mã giảm giá thành công",
                        promotionService.createCoupon(request)
                )
        );
    }

    @PutMapping("/coupons/{couponId}")
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

    @DeleteMapping("/coupons/{couponId}")
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

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<PageCouponAdminResponse>> listCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) String keyword)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách coupon thành công",
                        promotionService.listCoupons(page, size, status, keyword)
                )
        );
    }

    @PostMapping("/flash-sales")
    public ResponseEntity<ApiResponse<CreateFlashSaleAdminResponse>> createFlashSale(@Valid @RequestBody CreateAdminFlashSaleRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "Tạo flash sale thành công",
                        promotionService.createFlashSale(request)
                )
        );
    }

    @PostMapping("/flash-sales/{flashSaleId}/warmup")
    public ResponseEntity<ApiResponse<WarmupFlashSaleResponse>> warmup(@PathVariable String flashSaleId)
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã nạp flash sale vào Redis. Sẵn sàng!",
                        promotionService.warmup(flashSaleId)
                )
        );
    }
}
