package com.ice.promotionservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CouponCounterService {

    private static final String KEY_PREFIX_USAGE = "coupon:usage:";
    private static final String KEY_PREFIX_USER = "coupon:user:";

    private final StringRedisTemplate stringRedisTemplate;

    public Long getUsageRemaining(String code)
    {
        String raw = stringRedisTemplate.opsForValue().get(KEY_PREFIX_USAGE+code);
        return raw == null ? null : Long.parseLong(raw);
    }

    public Long getUserHadUsed(String code, String userId)
    {
        String raw = stringRedisTemplate.opsForValue().get(KEY_PREFIX_USER+code+":"+userId);
        return raw == null ? null : Long.parseLong(raw);
    }

    public Long incrementUsage(String code)
    {
        return stringRedisTemplate.opsForValue().increment(KEY_PREFIX_USAGE+code, 1);
    }

    public Long decrementUsage(String code)
    {
        return stringRedisTemplate.opsForValue().decrement(KEY_PREFIX_USAGE+code, 1);
    }

    public Long incrementUser(String code, String userId)
    {
        return stringRedisTemplate.opsForValue().increment(KEY_PREFIX_USER + code + ":" + userId, 1);
    }

    public Long decrementUser(String code, String userId)
    {
        return stringRedisTemplate.opsForValue().decrement(KEY_PREFIX_USER + code + ":" + userId, 1);
    }
}
