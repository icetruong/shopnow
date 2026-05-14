package com.qlda.userservice.Service;

import com.qlda.userservice.DTO.Request.Auth.LoginRequest;
import com.qlda.userservice.DTO.Request.Auth.RegisterRequest;
import com.qlda.userservice.DTO.Response.Auth.LoginResponse;
import com.qlda.userservice.DTO.Response.Auth.RegisterResponse;
import com.qlda.userservice.Entity.RefreshToken;
import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Exception.EmailExistException;
import com.qlda.userservice.Exception.ResourceNotFoundException;
import com.qlda.userservice.Repository.RefreshTokenRepo;
import com.qlda.userservice.Repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JWTService jwtService;
    private final RefreshTokenRepo refreshTokenRepo;

    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;

    @Value("${jwt.access-token-expiry}")
    private long accessTokenExpiry;

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

        return new RegisterResponse(save.getId(), save.getEmail());
    }

    public LoginResponse login(LoginRequest request)
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
        String refreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry))
                .build();

        refreshTokenRepo.save(refreshTokenEntity);

        return new LoginResponse(token, refreshToken, "Bearer", accessTokenExpiry);
    }
}
