package com.ice.notificationservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private static final String PROCESSED_KEY = "processed:event:";
    private final StringRedisTemplate stringRedisTemplate;

    public boolean isProcessed(String eventId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_KEY + eventId));
    }

    public void markProcessed(String eventId) {
        stringRedisTemplate.opsForValue().set(PROCESSED_KEY + eventId, "1", Duration.ofHours(24));
    }
}
