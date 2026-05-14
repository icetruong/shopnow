package com.qlda.userservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenResetPasswordService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "resetPassword:";

    public void resetPassword(String token, Instant expiresAt, String userId)
    {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if(!ttl.isNegative())
            redisTemplate.opsForValue().set(PREFIX+token,userId,ttl);
    }

    public String getUserIdResetPassword(String token)
    {
        return redisTemplate.opsForValue().get(PREFIX + token);
    }
}
