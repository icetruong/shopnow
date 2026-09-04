package com.ice.inventoryservice.Scheduler;

import com.ice.inventoryservice.Entity.FlashSaleStock;
import com.ice.inventoryservice.Repository.FlashSaleStockRepo;
import com.ice.inventoryservice.Service.FlashSaleSettler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlashSaleSettlementScheduler {
    private final FlashSaleStockRepo flashSaleStockRepo;
    private final FlashSaleSettler flashSaleSettler;

    @Scheduled(
            fixedDelayString   = "${flash.settlement.interval-ms:60000}",
            initialDelayString = "${flash.settlement.initial-delay-ms:20000}")
    public void settle()
    {
        List<FlashSaleStock> flashSaleStocks = flashSaleStockRepo.findAllBySettledAtIsNull();
        if(flashSaleStocks.isEmpty())
            return;

        for(FlashSaleStock flashSaleStock : flashSaleStocks)
        {
            try {
                flashSaleSettler.settleOne(flashSaleStock.getId());          // PK của row -> settler load lại (FOR UPDATE) trong TX mới
            } catch (Exception e) {
                log.error("Settle flash_sale_stocks id={} lỗi: {}", flashSaleStock.getId(), e.getMessage(), e);
                // nuốt lỗi 1 dòng -> mẻ sau retry; các dòng còn lại vẫn chạy
            }
        }
    }
}
