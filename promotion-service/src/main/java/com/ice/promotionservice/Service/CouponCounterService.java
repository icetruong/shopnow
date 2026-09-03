package com.ice.promotionservice.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Đọc "lượt còn lại" của nhiều coupon trong 1 lần MGET.
     * Trả map code -> remaining; giá trị null nghĩa là Redis chưa có key cho code đó.
     */
    public Map<String, Long> getUsageRemainingBatch(List<String> codes)
    {
        Map<String, Long> result = new HashMap<>();
        if (codes == null || codes.isEmpty())
            return result;

        List<String> keys = codes.stream().map(c -> KEY_PREFIX_USAGE + c).toList();
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

        for (int i = 0; i < codes.size(); i++) {
            String raw = (values == null) ? null : values.get(i);
            result.put(codes.get(i), raw == null ? null : Long.parseLong(raw));
        }
        return result;
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

    /**
     * INCRBY counter "lượt còn lại" theo delta (delta âm = giảm).
     * Dùng khi admin sửa usageLimit — cộng đúng phần chênh lệch, không SET đè để khỏi mất phần đã trừ.
     */
    public Long adjustUsageRemaining(String code, long delta)
    {
        return stringRedisTemplate.opsForValue().increment(KEY_PREFIX_USAGE + code, delta);
    }

    /** Cập nhật TTL của counter "lượt còn lại" khi admin đổi endsAt. */
    public void updateUsageTtl(String code, LocalDateTime endAt)
    {
        Duration ttl = Duration.between(LocalDateTime.now(), endAt);
        if (!ttl.isNegative() && !ttl.isZero())
            stringRedisTemplate.expire(KEY_PREFIX_USAGE + code, ttl);
    }

    public void deleteUsage(String code)
    {
        stringRedisTemplate.delete(KEY_PREFIX_USAGE+code);
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
