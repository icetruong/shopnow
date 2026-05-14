package com.qlda.userservice.Service;

import com.qlda.userservice.DTO.Request.Auth.*;
import com.qlda.userservice.DTO.Response.Auth.ForgotPasswordResponse;
import com.qlda.userservice.DTO.Response.Auth.RegisterResponse;
import com.qlda.userservice.DTO.Response.Auth.TokenResponse;
import com.qlda.userservice.Entity.RefreshToken;
import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Exception.*;
import com.qlda.userservice.Repository.RefreshTokenRepo;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final RefreshTokenRepo refreshTokenRepo;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenResetPasswordService tokenResetPasswordService;
    private final TokenVerifyEmailService tokenVerifyEmailService;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

    @Value("${jwt.reset-password-token-expiry}")
    private long resetPasswordTokenExpiry;

    public RegisterResponse register(RegisterRequest request)
    {
        if(userRepo.existsByEmail(request.getEmail()))
            throw new EmailExistException("Email already exists");

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .build();

        User save = userRepo.save(user);

        String token = jwtService.generateRandomToken();

        tokenVerifyEmailService.verifyToken(token, save.getId().toString());

        return new RegisterResponse(save.getId(), save.getEmail());
    }

    public TokenResponse login(LoginRequest request)
    {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("user not found" + authentication.getName()));

        String token = jwtService.generateAccessToken(authentication);
        String refreshToken = jwtService.generateRandomToken();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry))
                .build();

        refreshTokenRepo.save(refreshTokenEntity);

        return new TokenResponse(token, refreshToken, "Bearer", accessTokenExpiry);
    }

    public TokenResponse getTokenByRefreshToken(RefreshTokenRequest request)
    {
        RefreshToken refreshTokenEntity = refreshTokenRepo.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new ResourceNotFoundException("refreshToken không hợp lệ" + request.getRefreshToken()));

        if(refreshTokenEntity.isExpired())
        {
            refreshTokenRepo.delete(refreshTokenEntity);
            throw new InvalidTokenException("Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.");
        }

        User user = refreshTokenEntity.getUser();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );

        refreshTokenRepo.delete(refreshTokenEntity);

        String token = jwtService.generateAccessToken(authentication);
        String refreshToken = jwtService.generateRandomToken();

        RefreshToken new_refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry))
                .build();

        refreshTokenRepo.save(new_refreshTokenEntity);

        return new TokenResponse(token, refreshToken, "Bearer", accessTokenExpiry);
    }

    public void logout(Authentication authentication)
    {
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;

        String jti = jwtAuth.getToken().getId();
        Instant expiresAt = jwtAuth.getToken().getExpiresAt();

        tokenBlacklistService.blacklist(jti, expiresAt);

        User user = userRepo.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        refreshTokenRepo.deleteAllByUser(user);
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request)
    {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String token = jwtService.generateRandomToken();
        tokenResetPasswordService.resetPassword(token, Instant.now().plusSeconds(resetPasswordTokenExpiry), user.getId().toString());

        return new ForgotPasswordResponse(token, resetPasswordTokenExpiry);
    }

    public void resetPassword(ResetPasswordRequest request) {
        String userId = tokenResetPasswordService.getUserIdResetPassword(request.getToken());

        if(userId == null)
            throw new TokenResetPasswordException("Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");

        User user = userRepo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepo.save(user);
    }

    public void verifyEmail(String token)
    {
        String userId = tokenVerifyEmailService.getUserIdVifyToken(token);

        if(userId == null)
            throw new VerifyTokenInvalidException("Link xác thực không hợp lệ hoặc đã hết hạn.");

        User user = userRepo.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setEmailVerified(true);
        userRepo.save(user);
    }
}
