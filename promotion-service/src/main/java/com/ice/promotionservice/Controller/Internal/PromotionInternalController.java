package com.ice.promotionservice.Controller.Internal;

import com.ice.promotionservice.DTO.Request.Coupon.CouponApplyRequest;
import com.ice.promotionservice.DTO.Request.Coupon.CouponRollbackRequest;
import com.ice.promotionservice.DTO.Request.FlashSale.FlashSalePurchaseRequest;
import com.ice.promotionservice.DTO.Response.Coupon.CouponApplyResponse;
import com.ice.promotionservice.DTO.Response.FlashSale.FlashSalePurchaseResponse;
import com.ice.promotionservice.Service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal")
public class PromotionInternalController {

    private final PromotionService promotionService;

    @PostMapping("/coupons/apply")
    public ResponseEntity<CouponApplyResponse> apply(@Valid @RequestBody CouponApplyRequest request)
    {
        return ResponseEntity.ok(
                promotionService.apply(request)
        );
    }

    @PostMapping("/coupons/rollback")
    public ResponseEntity<Void> rollback(@Valid @RequestBody CouponRollbackRequest request)
    {
        promotionService.rollback(request);

        return ResponseEntity.ok(
                null
        );
    }

    @PostMapping("/flash-sales/purchase")
    public ResponseEntity<FlashSalePurchaseResponse> purchase(@Valid @RequestBody FlashSalePurchaseRequest request)
    {
        return ResponseEntity.ok(
                promotionService.purchase(request)
        );
    }
}
