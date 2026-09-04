package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.FlashPurchasedPayload;
import com.ice.inventoryservice.DTO.Request.Admin.FlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.inventoryservice.Exception.*;
import com.ice.inventoryservice.Repository.FlashSaleStockRepo;
import com.ice.inventoryservice.Repository.InventoryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
    private final FlashSaleStockRepo flashSaleStockRepo;
    private final InventoryRepo inventoryRepo;
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> flashReserveScript;
    private final KafkaProducerService kafkaProducerService;

    // Redis key constants
    private static final String KEY_ACTIVE = "flash:active:%s";
    private static final String KEY_USER   = "flash:user:%s:%s:%s";
    private static final String KEY_STOCK  = "flash:stock:%s:%s";
    private static final String KEY_DONE   = "flash:done:%s:%s";

    public FlashSaleReserveResponse reserve(FlashSaleReserveRequest request)
    {
        String activeKey = KEY_ACTIVE.formatted(request.getFlashSaleId());
        String userKey = KEY_USER.formatted(request.getFlashSaleId(), request.getVariantId(), request.getUserId());
        String stockKey = KEY_STOCK.formatted(request.getFlashSaleId(), request.getVariantId());
        String doneKey = KEY_DONE.formatted(request.getOrderId(), request.getVariantId());

        Long ttl = redisTemplate.getExpire(activeKey);
        if(ttl == null || ttl <= 0L)
            throw new FlashSaleNotActiveException();

        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(doneKey, "1", Duration.ofSeconds(ttl));

        if(firstTime == null)
            throw new IllegalStateException("Redis không phản hồi khi set idempotency key");

        if(!firstTime)
        {
            String cur = redisTemplate.opsForValue().get(stockKey);
            long remaining = (cur == null) ? 0L : Long.parseLong(cur);

            return new FlashSaleReserveResponse(
                    true,
                    remaining,
                    Instant.now()
            );
        }

        Long result = redisTemplate.execute(
                flashReserveScript,
                List.of(stockKey, userKey, activeKey),
                String.valueOf(request.getQty()),
                String.valueOf(request.getLimitPerUser()),
                String.valueOf(ttl)
        );

        if(result == null || result < 0)
        {
            redisTemplate.delete(doneKey);
            if (result == null) throw new IllegalStateException("Lua script trả null");
            switch (result.intValue()) {
                case -3 -> throw new FlashSaleNotActiveException();
                case -2 -> throw new FlashSaleUserLimitException();
                default -> throw new FlashSaleSoldOutException("Sản phẩm flash sale đã hết."); // -1
            }
        }

        kafkaProducerService.publishFlashPurchased(new FlashPurchasedPayload(
                request.getFlashSaleId(),
                request.getVariantId(),
                request.getUserId(),
                request.getOrderId(),
                request.getQty()
        ));

        return new FlashSaleReserveResponse(
                true,
                result,
                Instant.now()
        );
    }


    @Transactional
    public void createdFlashSale(FlashSaleRequest request)
    {

    }
}
