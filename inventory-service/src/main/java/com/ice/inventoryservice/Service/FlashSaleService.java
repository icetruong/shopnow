package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.FlashPurchasedPayload;
import com.ice.inventoryservice.DTO.Request.Admin.FlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Admin.ItemFlashSaleRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.FlashSaleReleaseRequest;
import com.ice.inventoryservice.DTO.Request.Inventory.FlashSaleReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.FlashSaleReserveResponse;
import com.ice.inventoryservice.Entity.FlashSaleStock;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Enum.ErrorCode;
import com.ice.inventoryservice.Exception.*;
import com.ice.inventoryservice.Repository.FlashSaleStockRepo;
import com.ice.inventoryservice.Repository.InventoryRepo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Qualifier("flashReserveScript")
    private final DefaultRedisScript<Long> flashReserveScript;
    @Qualifier("flashReleaseScript")
    private final DefaultRedisScript<Long> flashReleaseScript;
    private final KafkaProducerService kafkaProducerService;

    // Redis key constants
    private static final String KEY_ACTIVE = "flash:active:%s";
    private static final String KEY_USER   = "flash:user:%s:%s:%s";
    private static final String KEY_STOCK  = "flash:stock:%s:%s";
    private static final String KEY_DONE   = "flash:done:%s:%s";
    private static final int STOCK_KEY_GRACE_MINUTES = 10;

    /** Chuẩn hoá UUID về dạng canonical để key Redis luôn khớp giữa các request/endpoint. */
    private static String norm(String uuid) {
        return UUID.fromString(uuid).toString();
    }

    public FlashSaleReserveResponse reserve(FlashSaleReserveRequest request)
    {
        String fs = norm(request.getFlashSaleId());
        String variantId = norm(request.getVariantId());
        String userId = norm(request.getUserId());
        String orderId = norm(request.getOrderId());

        String activeKey = KEY_ACTIVE.formatted(fs);
        String userKey = KEY_USER.formatted(fs, variantId, userId);
        String stockKey = KEY_STOCK.formatted(fs, variantId);
        String doneKey = KEY_DONE.formatted(orderId, variantId);

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

    public void release(FlashSaleReleaseRequest request) {
        String fs = norm(request.getFlashSaleId());
        String variantId = norm(request.getVariantId());
        String userId = norm(request.getUserId());
        String orderId = norm(request.getOrderId());

        String userKey = KEY_USER.formatted(fs, variantId, userId);
        String stockKey = KEY_STOCK.formatted(fs, variantId);
        String doneKey = KEY_DONE.formatted(orderId, variantId);

        Long result = redisTemplate.execute(
                flashReleaseScript,
                List.of(stockKey,userKey,doneKey),
                String.valueOf(request.getQty())
        );

        if (result == null)
            throw new IllegalStateException("Lua script trả null");
    }


    @Transactional
    public void createdFlashSale(FlashSaleRequest request)
    {
        Instant now = Instant.now();
        if(!request.getStartsAt().isBefore(request.getEndsAt()))
            throw new IllegalArgumentException("startsAt phải trước endsAt.");
        if(!request.getEndsAt().isAfter(now))
            throw new IllegalArgumentException("endsAt phải ở tương lai.");

        UUID flashSaleId = UUID.fromString(request.getFlashSaleId());
        if(flashSaleStockRepo.existsByFlashSaleId(flashSaleId))
            throw new FlashSaleAlreadyExistsException();

        Map<UUID, Integer> itemsMap = new HashMap<>();
        List<UUID> variantIds = new ArrayList<>();

        for (ItemFlashSaleRequest itemFlashSaleRequest : request.getItems())
        {
            UUID variantId = UUID.fromString(itemFlashSaleRequest.getVariantId());
            itemsMap.put(variantId, itemFlashSaleRequest.getFlashSaleQty());
            variantIds.add(variantId);
        }

        List<Inventory> inventories = inventoryRepo.findAllByVariantIdInOrderByVariantId(variantIds);

        if(inventories.size() < variantIds.size())
            throw new ResourceNotFoundException("Một số variantId chưa có inventory.", ErrorCode.INVENTORY_NOT_FOUND);

        for (Inventory inventory : inventories)
        {
            Integer qty = itemsMap.get(inventory.getVariantId());
            if(qty > inventory.getAvailableQty())
                throw new NotEnoughToUserForFlashSale("Không đủ tồn kho để khởi tạo flash sale cho variant " + inventory.getVariantId());
        }

        // 1) Ghi DB trước + flush ngay -> nếu vi phạm unique (race), rollback xảy ra TRƯỚC khi đụng Redis
        List<FlashSaleStock> flashSaleStocks = new ArrayList<>();
        for (Inventory inventory : inventories)
        {
            inventory.setReservedQty(inventory.getReservedQty()+itemsMap.get(inventory.getVariantId()));
            flashSaleStocks.add(FlashSaleStock.builder()
                    .flashSaleId(flashSaleId)
                    .variantId(inventory.getVariantId())
                    .initialQty(itemsMap.get(inventory.getVariantId()))
                    .startsAt(LocalDateTime.ofInstant(request.getStartsAt(), ZoneOffset.UTC))
                    .endsAt(LocalDateTime.ofInstant(request.getEndsAt(), ZoneOffset.UTC))
                    .build());
        }
        try {
            flashSaleStockRepo.saveAll(flashSaleStocks);
            flashSaleStockRepo.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new FlashSaleAlreadyExistsException();
        }

        // 2) Redis: chỉ nạp counter tồn kho. flash:active KHÔNG bật ở đây
        //    -> bật riêng qua activate() đúng startsAt (Promotion Service gọi).
        Duration ttl = Duration.between(now, request.getEndsAt()).plusMinutes(STOCK_KEY_GRACE_MINUTES);
        for (Inventory inventory : inventories)
        {
            String stockKey = KEY_STOCK.formatted(flashSaleId.toString(), inventory.getVariantId().toString());
            redisTemplate.opsForValue().set(stockKey, itemsMap.get(inventory.getVariantId()).toString(), ttl);
        }
    }

    /**
     * Bật cờ flash:active cho flash sale — Promotion Service gọi đúng thời điểm startsAt.
     * Idempotent (SET NX): gọi lại nhiều lần / nhiều instance đều vô hại.
     */
    public void activate(UUID flashSaleId)
    {
        List<FlashSaleStock> rows = flashSaleStockRepo.findAllByFlashSaleId(flashSaleId);
        if(rows.isEmpty())
            throw new ResourceNotFoundException("Chưa khởi tạo tồn kho cho flash sale này.", ErrorCode.FLASH_SALE_NOT_FOUND);

        LocalDateTime endsAt = rows.get(0).getEndsAt();
        long ttl = Duration.between(LocalDateTime.now(ZoneOffset.UTC), endsAt).getSeconds();
        if(ttl <= 0L)
            throw new FlashSaleNotActiveException();

        String activeKey = KEY_ACTIVE.formatted(flashSaleId.toString());
        redisTemplate.opsForValue().setIfAbsent(activeKey, "1", Duration.ofSeconds(ttl));
    }
}
