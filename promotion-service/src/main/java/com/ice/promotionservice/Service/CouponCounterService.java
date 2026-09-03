package com.ice.promotionservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

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

    public Long incrementUsage(String code)
    {
        return stringRedisTemplate.opsForValue().increment(KEY_PREFIX_USAGE+code, 1);
    }

    public Long decrementUsage(String code)
    {
        return stringRedisTemplate.opsForValue().decrement(KEY_PREFIX_USAGE+code, 1);
    }

    public void createUsageRemaining(Long usageLimit, String code, LocalDateTime endAt)
    {
        stringRedisTemplate.opsForValue().set(KEY_PREFIX_USAGE+code, usageLimit.toString(), Duration.between(LocalDateTime.now(), endAt));
    }

    public Long getUserHadUsed(String code, String userId)
    {
        String raw = stringRedisTemplate.opsForValue().get(KEY_PREFIX_USER+code+":"+userId);
        return raw == null ? null : Long.parseLong(raw);
    }

    /**
     * INCR counter "user này đã dùng coupon mấy lần".
     * Lần đầu key được tạo (kết quả = 1) thì gắn TTL = thời gian còn lại của coupon,
     * để key tự hết hạn thay vì nằm lại Redis vĩnh viễn.
     */
    public Long incrementUser(String code, String userId, LocalDateTime endAt)
    {
        String key = KEY_PREFIX_USER + code + ":" + userId;
        Long value = stringRedisTemplate.opsForValue().increment(key, 1);

        if (value != null && value == 1L) {
            Duration ttl = Duration.between(LocalDateTime.now(), endAt);
            if (!ttl.isNegative() && !ttl.isZero())
                stringRedisTemplate.expire(key, ttl);
        }
        return value;
    }

    public Long decrementUser(String code, String userId)
    {
        return stringRedisTemplate.opsForValue().decrement(KEY_PREFIX_USER + code + ":" + userId, 1);
    }
}
