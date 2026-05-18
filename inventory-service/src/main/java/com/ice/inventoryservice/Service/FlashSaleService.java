package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.FlashSaleReservedEvent;
import com.ice.inventoryservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Exception.FlashSaleSoldOutException;
import com.ice.inventoryservice.Exception.FlashSaleUserLimitException;
import com.ice.inventoryservice.Exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
    private final StringRedisTemplate redisTemplate;
    private final KafkaProducerService kafkaProducerService;

    // Redis key constants
    private static final String KEY_ACTIVE  = "flash:active:%s";
    private static final String KEY_USER    = "flash:user:%s:%s";
    private static final String KEY_STOCK   = "flash:stock:%s:%s";

    public FlashSaleReserveResponse reserve(FlashSaleReserveRequest request)
    {
        String activeKey = KEY_ACTIVE.formatted(request.getFlashSaleId());
        String userKey = KEY_USER.formatted(request.getFlashSaleId(), request.getUserId());
        String stockKey = KEY_STOCK.formatted(request.getFlashSaleId(), request.getVariantId());

        if(!Boolean.TRUE.equals(redisTemplate.hasKey(activeKey)))
            throw new ResourceNotFoundException("flash sale not active", ErrorCode.FLASH_SALE_NOT_FOUND);

        if(Boolean.TRUE.equals(redisTemplate.hasKey(userKey)))
            throw new FlashSaleUserLimitException();

        Long remaining = redisTemplate.opsForValue().decrement(stockKey);

        if(remaining == null || remaining < 0)
        {
            redisTemplate.opsForValue().increment(stockKey);
            throw new FlashSaleSoldOutException("sold out");
        }

        Long ttl = redisTemplate.getExpire(activeKey);
        if(ttl != null && ttl > 0)
            redisTemplate.opsForValue().set(userKey, "1", Duration.ofSeconds(ttl));
        else
            redisTemplate.opsForValue().set(userKey, "1", Duration.ofHours(1));

        Instant now = Instant.now();
        kafkaProducerService.publishFlashSaleEvent(new FlashSaleReservedEvent(
                request.getFlashSaleId(),
                request.getVariantId(),
                request.getOrderId(),
                request.getUserId(),
                request.getQty(),
                now
        ));

        return new FlashSaleReserveResponse(
                true,
                remaining,
                now
        );




    }
}
