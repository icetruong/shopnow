package com.qlda.userservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenVerifyEmailService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "verify-token:";

    public void verifyToken(String token, String userId)
    {
        redisTemplate.opsForValue().set(PREFIX+token,userId,Duration.ofHours(24));
    }

    public String getUserIdVifyToken(String token)
    {
        return redisTemplate.opsForValue().get(PREFIX + token);
    }
}
