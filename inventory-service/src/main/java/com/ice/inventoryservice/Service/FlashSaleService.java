package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.FlashSaleReservedEvent;
import com.ice.inventoryservice.DTO.Request.Admin.FlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Admin.ItemFlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.ItemReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.inventoryservice.Entity.FlashSaleStock;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Exception.FlashSaleSoldOutException;
import com.ice.inventoryservice.Exception.FlashSaleUserLimitException;
import com.ice.inventoryservice.Exception.NotEnoughToUserForFlashSale;
import com.ice.inventoryservice.Exception.ResourceNotFoundException;
import com.ice.inventoryservice.Repository.FlashSaleStockRepo;
import com.ice.inventoryservice.Repository.InventoryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
    private final FlashSaleStockRepo flashSaleStockRepo;
    private final InventoryRepo inventoryRepo;
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

    public void createdFlashSale(FlashSaleRequest request)
    {
        Map<UUID, Integer> itemsMap = new HashMap<>();
        List<UUID> variantIds = new ArrayList<>();
        for(ItemFlashSaleRequest item: request.getItems())
        {
            UUID variantId = UUID.fromString(item.getVariantId());
            itemsMap.put(variantId, item.getFlashSaleQty());
            variantIds.add(variantId);
        }

        List<Inventory> inventories = inventoryRepo.findAllByVariantIdIn(variantIds);

        if(inventories.size() != variantIds.size())
            throw new ResourceNotFoundException("has variantIds not found in inventory", ErrorCode.INVENTORY_NOT_FOUND);

        for(Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());
            if(inventory.getStockQty() < qty)
                throw new NotEnoughToUserForFlashSale("not stock quantity to create Flash Sale");
        }

        List<FlashSaleStock> flashSaleStocks = new ArrayList<>();

        String activeKey = KEY_ACTIVE.formatted(request.getFlashSaleId());
        Duration ttl = Duration.between(Instant.now(), request.getEndsAt());
        redisTemplate.opsForValue().set(
                activeKey, "1", ttl
        );

        for(Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());

            String stockKey = KEY_STOCK.formatted(request.getFlashSaleId(), inventory.getVariantId().toString());

            redisTemplate.opsForValue().set(
                    stockKey, String.valueOf(qty), ttl
            );
            flashSaleStocks.add(FlashSaleStock.builder()
                    .flashSaleId(UUID.fromString(request.getFlashSaleId()))
                    .variantId(inventory.getVariantId())
                    .initialQty(qty)
                    .startsAt(LocalDateTime.ofInstant(request.getStartsAt(), ZoneOffset.UTC))
                    .endsAt(LocalDateTime.ofInstant(request.getEndsAt(), ZoneOffset.UTC))
                    .build()
            );


        }
        flashSaleStockRepo.saveAll(flashSaleStocks);
    }
}
