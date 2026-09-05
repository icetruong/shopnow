package com.ice.apigateway.Config;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startTime = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] {} {} — {} — {} — {}ms",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    resolveUserId(request),
                    response.getStatus(),
                    duration);
        }
    }

    /**
     * Đọc claim "userId" trực tiếp từ token trong header, KHÔNG verify chữ ký.
     * Chỉ dùng để log/trace — quyết định auth thật sự nằm ở SecurityFilterChain chạy sau filter này,
     * không dựa vào giá trị đọc ở đây. Không thể dùng SecurityContextHolder vì Spring Security đã
     * clear context trước khi filterChain.doFilter() ở trên trả về (filter này bọc ngoài Security).
     */
    private String resolveUserId(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return "anonymous";
        }
        try {
            JWTClaimsSet claims = JWTParser.parse(header.substring(7)).getJWTClaimsSet();
            String userId = claims.getStringClaim("userId");
            return userId != null ? userId : "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }
}
