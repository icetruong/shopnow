package com.ice.cartservice.Controller;

import com.ice.cartservice.DTO.Request.Cart.CartItemAddRequest;
import com.ice.cartservice.DTO.Request.Cart.CartItemUpdateRequest;
import com.ice.cartservice.DTO.Response.Cart.CartItemAddResponse;
import com.ice.cartservice.DTO.Response.Cart.CartItemDeleteResponse;
import com.ice.cartservice.DTO.Response.Cart.CartItemUpdateResponse;
import com.ice.cartservice.DTO.Response.Cart.ListCartItemResponse;
import com.ice.cartservice.DTO.Response.Common.ApiResponse;
import com.ice.cartservice.Service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<ListCartItemResponse>> getCart(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        ListCartItemResponse data = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy giỏ hàng thành công", data));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartItemAddResponse>> addCartItem(Authentication authentication, @Valid @RequestBody CartItemAddRequest request)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã thêm vào giỏ hàng.",
                        cartService.addCart(userId, request)
                )
        );
    }

    @PutMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemUpdateResponse>> updateCartItem(Authentication authentication,@PathVariable String cartItemId, @Valid @RequestBody CartItemUpdateRequest request)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã cập nhật số lượng sản phẩm.",
                        cartService.updateCart(userId, cartItemId, request)
                )
        );
    }

    @DeleteMapping("/items/{cartItemId}")
    public ResponseEntity<ApiResponse<CartItemDeleteResponse>> deleteCartItem(Authentication authentication, @PathVariable String cartItemId)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã xóa sản phẩm khỏi giỏ hàng.",
                        cartService.deleteCartItem(userId, cartItemId)
                )
        );
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteCart(Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        cartService.deleteCart(userId);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã xóa giỏ hàng.",
                        null
                )
        );
    }
}
