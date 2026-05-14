package com.qlda.userservice.Config;

import com.qlda.userservice.Entity.RefreshToken;
import com.qlda.userservice.Entity.User;
import com.qlda.userservice.Exception.ResourceNotFoundException;
import com.qlda.userservice.Repository.RefreshTokenRepo;
import com.qlda.userservice.Repository.UserRepo;
import com.qlda.userservice.Service.JWTService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JWTService jwtService;
    private final UserRepo userRepo;
    private final RefreshTokenRepo refreshTokenRepo;
    private final HttpCookieOauth2AuthorizationRequestRepository cookieRepo;

    @Value("${oauth2.frontend-redirect-uri}")
    private String frontendRedirectUri;
    @Value("${jwt.refresh-token-expiry}")
    private long refreshTokenExpiry;


    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        String email = ((DefaultOAuth2User) authentication.getPrincipal()).getAttribute("email");
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(), null,
                List.of(new SimpleGrantedAuthority(user.getRole().name()))
        );

        String accessToken = jwtService.generateAccessToken(auth);
        String refreshToken = jwtService.generateRandomToken();

        RefreshToken new_refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpiry))
                .build();

        refreshTokenRepo.save(new_refreshTokenEntity);

        cookieRepo.removeAuthorizationRequest(request, response);

        getRedirectStrategy().sendRedirect(request, response,
                frontendRedirectUri + "#accessToken=" + accessToken + "&refreshToken=" + refreshToken);

    }
}
