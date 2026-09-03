package com.ice.promotionservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CouponUsageCounter {

    private static final String KEY_PREFIX = "coupon:usage:";

    private final StringRedisTemplate stringRedisTemplate;

    public Long getRemaining(String code)
    {
        String raw = stringRedisTemplate.opsForValue().get(KEY_PREFIX+code);
        return raw == null ? null : Long.parseLong(raw);
    }
}
