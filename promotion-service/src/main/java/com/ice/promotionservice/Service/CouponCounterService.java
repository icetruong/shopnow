package com.ice.promotionservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CouponCounter {

    private static final String KEY_PREFIX_USAGE = "coupon:usage:";
    private static final String KEY_PREFIX_USER = ""

    private final StringRedisTemplate stringRedisTemplate;

    public Long getUsageRemaining(String code)
    {
        String raw = stringRedisTemplate.opsForValue().get(KEY_PREFIX_USAGE+code);
        return raw == null ? null : Long.parseLong(raw);
    }
}
