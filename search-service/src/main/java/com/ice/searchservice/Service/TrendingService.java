package com.ice.searchservice.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {
    private static final String KEY_PREFIX   = "search:trending:";
    private static final Duration TTL        = Duration.ofDays(14);
    private static final int DEFAULT_LIMIT   = 10;
    private static final int MAX_LIMIT       = 50;
    private static final int MIN_TERM_LEN    = 2;
    private static final int MAX_TERM_LEN    = 100;

    private final StringRedisTemplate stringRedisTemplate;

    public void recordSearchTerm(String q)
    {
        String term = normalize(q);
        if(term == null)
            return;

        try {
            String key = weekKey();
            stringRedisTemplate.opsForZSet().incrementScore(key, term, 1);
            stringRedisTemplate.expire(key, TTL);
        }
        catch (Exception e)
        {
            log.warn("Không ghi được trending term='{}'", term, e);
        }
    }

    public List<String> getTrending(int limit)
    {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);

        try {
            String key = weekKey();
            Set<String> top = stringRedisTemplate.opsForZSet().reverseRange(key, 0 , safeLimit-1);
            if (top != null && top.size() >= safeLimit)
                return List.copyOf(top);

            LinkedHashSet<String> merged = new LinkedHashSet<>(top == null ? Set.of() : top);
            String preKey = preWeekKey();
            Set<String> pre = stringRedisTemplate.opsForZSet().reverseRange(preKey, 0 , safeLimit-1);

            if(pre!= null)
                merged.addAll(pre);

            return merged.stream().limit(safeLimit).toList();
        }
        catch (Exception e)
        {
            log.warn("Không đọc được trending", e);
            return List.of();
        }
    }

    private static String normalize(String q) {
        if (q == null) return null;
        String t = q.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return (t.length() < MIN_TERM_LEN || t.length() > MAX_TERM_LEN) ? null : t;
    }

    private static String weekKey() {
        LocalDate now = LocalDate.now();
        int year = now.get(WeekFields.ISO.weekBasedYear());
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        return KEY_PREFIX + year + "-W" + String.format("%02d", week);
    }

    private static String preWeekKey()
    {
        LocalDate pre = LocalDate.now().minusWeeks(1);
        int year = pre.get(WeekFields.ISO.weekBasedYear());
        int week = pre.get(WeekFields.ISO.weekOfWeekBasedYear());
        return KEY_PREFIX + year + "-W" + String.format("%02d", week);
    }
}
