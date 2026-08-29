package com.ice.notificationservice.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    /** Cache số thông báo chưa đọc: key Redis = "noti:unread:{userId}". */
    public static final String CACHE_UNREAD_COUNT = "noti:unread";

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory factory) {
        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer.builder().build();

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                // mặc định Spring dùng "cacheName::key" -> đổi thành "cacheName:key" cho đúng spec
                .computePrefixWith(cacheName -> cacheName + ":")
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        // TTL an toàn 1 ngày để lệch tự lành
        // (spec ghi "không TTL" nhưng TTL=ZERO + quên evict 1 chỗ = sai vĩnh viễn).
        RedisCacheConfiguration unreadConfig = base.entryTtl(Duration.ofDays(1));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withCacheConfiguration(CACHE_UNREAD_COUNT, unreadConfig)
                .build();
    }

    /** Redis lỗi thì log rồi bỏ qua, không được làm sập request. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache GET lỗi [{}] key={}", cache.getName(), key, ex);
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Cache PUT lỗi [{}] key={}", cache.getName(), key, ex);
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Cache EVICT lỗi [{}] key={}", cache.getName(), key, ex);
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.warn("Cache CLEAR lỗi [{}]", cache.getName(), ex);
            }
        };
    }
}
