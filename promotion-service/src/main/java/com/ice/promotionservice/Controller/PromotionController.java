package com.ice.promotionservice.Controller;

import com.ice.promotionservice.DTO.Request.Coupon.ValidationCouponRequest;
import com.ice.promotionservice.DTO.Response.Common.ApiResponse;
import com.ice.promotionservice.DTO.Response.Coupon.CouponUserResponse;
import com.ice.promotionservice.DTO.Response.Coupon.ValidationCouponResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<CouponUserResponse>>> getCouponForUser(
            Authentication authentication
    )
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách coupon thành công",
                        promotionService.getCouponForUser(userId)
                )
        );
    }
}
