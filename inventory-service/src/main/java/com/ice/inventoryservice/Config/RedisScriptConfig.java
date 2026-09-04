package com.ice.inventoryservice.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long>  flashReserveScript()
    {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/flash_reserve.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
