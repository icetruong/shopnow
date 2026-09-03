package com.ice.orderservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class Idempotency {
    private final StringRedisTemplate stringRedisTemplate;

    private static final String PROCESSED_KEY = "processed:event:";

    public Boolean isProcessed(String eventId)
    {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_KEY+eventId));
    }

    public void markProcessed(String eventId)
    {
        stringRedisTemplate.opsForValue().set(PROCESSED_KEY+eventId, "1", Duration.ofHours(24));
    }

}
