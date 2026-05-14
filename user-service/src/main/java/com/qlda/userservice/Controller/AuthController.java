package com.qlda.userservice.Controller;

import com.qlda.userservice.DTO.Request.Auth.LoginRequest;
import com.qlda.userservice.DTO.Request.Auth.RefreshTokenRequest;
import com.qlda.userservice.DTO.Request.Auth.RegisterRequest;
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

    @GetMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@RequestBody RefreshTokenRequest request, Authentication authentication)
    {
        return ResponseEntity.ok(ApiResponse.success(
                "Dùng Refresh token đổi lấy token thành công",
                userService.getTokenByRefreshToken(request, authentication)
        ));
    }
}
