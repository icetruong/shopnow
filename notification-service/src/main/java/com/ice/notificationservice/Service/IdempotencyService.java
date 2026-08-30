package com.ice.notificationservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdempotencyCheckService {
    private static final String PROCESSED_KEY = "processed:event:";
    private final StringRedisTemplate stringRedisTemplate;

    private void markProcessed(String eventId)
    {
        
    }
}
