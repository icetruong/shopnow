package com.ice.cartservice.Controller.Internal;

import com.ice.cartservice.DTO.Request.Cart.CartItemCheckoutConfirmRequest;
import com.ice.cartservice.DTO.Response.Cart.CartDataCheckoutTokenResponse;
import com.ice.cartservice.DTO.Response.Common.ApiResponse;
import com.ice.cartservice.Service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/cart")
public class CartInternalController {
    private final CartService cartService;

    @PostMapping("/checkout/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmCheckout(@Valid @RequestBody CartItemCheckoutConfirmRequest request)
    {
        cartService.confirmCheckout(request);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã xóa items đã đặt khỏi giỏ hàng.",
                        null
                )
        );
    }

    @GetMapping("/checkout/{checkoutToken}")
    public ResponseEntity<CartDataCheckoutTokenResponse> getDataCheckout(@PathVariable String checkoutToken)
    {
        return ResponseEntity.ok(
                cartService.getDataCheckout(checkoutToken)
        );
    }
}
