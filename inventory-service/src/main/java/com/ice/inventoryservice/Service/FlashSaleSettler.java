package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.Entity.FlashSaleStock;
import com.ice.inventoryservice.Entity.Inventory;
import com.ice.inventoryservice.Entity.StockTransaction;
import com.ice.inventoryservice.Enum.StockTransactionType;
import com.ice.inventoryservice.Repository.FlashSaleStockRepo;
import com.ice.inventoryservice.Repository.InventoryRepo;
import com.ice.inventoryservice.Repository.StockTransactionRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleSettler {
    private final StockTransactionRepo stockTransactionRepo;
    private final InventoryRepo inventoryRepo;
    private final FlashSaleStockRepo flashSaleStockRepo;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_STOCK = "flash:stock:%s:%s";

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settleOne(UUID rowId)
    {
        // Khoá row flash_sale_stocks -> mẻ chạy chồng (nhiều instance) sẽ chờ và đọc lại soldQty mới
        FlashSaleStock flashSaleStock = flashSaleStockRepo.findByIdForUpdate(rowId)
                .orElse(null);

        if(flashSaleStock == null || flashSaleStock.getSettledAt() != null)
            return;

        String stockKey = KEY_STOCK.formatted(flashSaleStock.getFlashSaleId().toString(), flashSaleStock.getVariantId().toString());

        String raw = redisTemplate.opsForValue().get(stockKey);
        Integer remaining = (raw == null) ? null : Integer.parseInt(raw.trim());
        boolean isFinal = flashSaleStock.getEndsAt().isBefore(LocalDateTime.now(ZoneOffset.UTC));

        if(remaining == null && !isFinal)
        {
            log.warn("flash:stock mất sớm trước endsAt: fs={} variant={} — chờ mẻ sau",
                    flashSaleStock.getFlashSaleId(), flashSaleStock.getVariantId().toString());
            return;
        }

        int soldNow = flashSaleStock.getSoldQty();
        if (remaining != null) {
            soldNow = Math.max(0, Math.min(flashSaleStock.getInitialQty(), flashSaleStock.getInitialQty() - remaining));
        }

        int delta = soldNow - flashSaleStock.getSoldQty();
        int remainder = isFinal ? flashSaleStock.getInitialQty() - soldNow : 0;

        if(delta != 0 || remainder != 0)
        {
            Inventory inventory = lockInventory(flashSaleStock.getVariantId());
            int before = inventory.getStockQty();
            if(delta != 0)
            {
                inventory.setStockQty(before - delta);
                inventory.setReservedQty(safeMinus(inventory.getReservedQty(), delta, flashSaleStock.getVariantId(), "delta"));
                inventory.setSoldQty(inventory.getSoldQty() + delta);

                stockTransactionRepo.save(StockTransaction.builder()
                        .variantId(flashSaleStock.getVariantId())
                        .type(StockTransactionType.FLASH_SALE)
                        .qty(-delta)
                        .qtyBefore(before)
                        .qtyAfter(inventory.getStockQty())
                        .orderId(null)
                        .note("Đối soát flash sale " + flashSaleStock.getFlashSaleId() + " @" + LocalDateTime.now(ZoneOffset.UTC))
                        .build()
                );

                flashSaleStock.setSoldQty(soldNow);
            }

            if(remainder != 0)
                inventory.setReservedQty(safeMinus(inventory.getReservedQty(), remainder, flashSaleStock.getVariantId(), "remainder"));
        }

        if(isFinal)
        {
            flashSaleStock.setSettledAt(LocalDateTime.now(ZoneOffset.UTC));
            redisTemplate.delete(stockKey);
        }
    }

    private Inventory lockInventory(UUID variantId) {
        List<Inventory> locked = inventoryRepo.findAllByVariantIdInOrderByVariantId(List.of(variantId));
        if (locked.isEmpty())
            throw new IllegalStateException("Không có inventory cho variant " + variantId);
        return locked.get(0);
    }

    private int safeMinus(int current, int amount, UUID variantId, String tag) {
        int result = current - amount;
        if (result < 0) {
            log.warn("reservedQty âm khi settle ({}): variant={} current={} minus={}",
                    tag, variantId, current, amount);
            return 0;   // clamp phòng thủ; xuất hiện log này = có bug cần soi
        }
        return result;
    }
}
