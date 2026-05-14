package com.qlda.userservice.Controller;

import com.qlda.userservice.DTO.Request.Auth.*;
import com.qlda.userservice.DTO.Response.Auth.ForgotPasswordResponse;
import com.qlda.userservice.DTO.Response.Auth.RegisterResponse;
import com.qlda.userservice.DTO.Response.Auth.TokenResponse;
import com.qlda.userservice.DTO.Response.Common.ApiResponse;
import com.qlda.userservice.Service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@RequestBody RegisterRequest request)
    {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản",
                        userService.register(request)
                ));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest request)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "login thành công",
                userService.login(request)
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshTokenRequest request)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Dùng Refresh token đổi lấy token thành công",
                userService.getTokenByRefreshToken(request)
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(Authentication authentication)
    {
        userService.logout(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                "Đăng xuất thành công", null

        ));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(@RequestBody ForgotPasswordRequest request)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Email có thể đổi mật khẩu.",
                userService.forgotPassword(request)
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@RequestBody ResetPasswordRequest request)
    {
        userService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success(
                "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại",
                null
        ));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token)
    {
        userService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.success(
                "Email đã được xác thực thành công.",
                null
        ));
    }
}
