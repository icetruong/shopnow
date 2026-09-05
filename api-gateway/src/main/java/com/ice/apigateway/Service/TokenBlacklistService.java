package com.qlda.userservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "blacklist:";

    public void blacklist(String jti, Instant expiresAt)
    {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if(!ttl.isNegative())
            redisTemplate.opsForValue().set(PREFIX+jti,"1",ttl);
    }

    public boolean isBlacklisted(String jti)
    {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }
}
