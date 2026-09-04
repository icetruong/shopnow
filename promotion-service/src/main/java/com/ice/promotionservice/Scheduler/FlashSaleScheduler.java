package com.ice.promotionservice.Scheduler;

import com.ice.promotionservice.Client.InventoryClient;
import com.ice.promotionservice.Entity.FlashSale;
import com.ice.promotionservice.Enum.FlashSaleStatus;
import com.ice.promotionservice.Repository.FlashSaleRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class FlashSaleScheduler {

    private final FlashSaleRepo flashSaleRepo;
    private final InventoryClient inventoryClient;

    @Scheduled(fixedDelayString = "${flash-sale.activation.poll-ms:5000}")
    public void activateDueFlashSales()
    {
        List<FlashSale> flashSales = flashSaleRepo.findDueForActivation(LocalDateTime.now());

        for(FlashSale fs : flashSales)
        {
            try {
                activateOne(fs);
            } catch (Exception e) {
                // 1 cái lỗi không chặn cái khác; tick sau tự retry
                log.warn("Activate flash sale {} lỗi: {}", fs.getId(), e.getMessage());
            }
        }
    }

    private void activateOne(FlashSale fs) {
        inventoryClient.activateFlashSale(fs.getId().toString());
        fs.setStatus(FlashSaleStatus.ACTIVE);
        flashSaleRepo.save(fs);
    }
}
