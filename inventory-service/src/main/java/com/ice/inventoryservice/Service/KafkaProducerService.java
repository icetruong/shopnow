package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.*;
import com.ice.inventoryservice.DTO.Request.Inventory.ItemReserveRequest;
import com.ice.inventoryservice.DTO.Response.Inventory.ReleaseResponse;
import com.ice.inventoryservice.Entity.Inventory;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String FLASH_SALE = "flash-sale-reserved";
    private static final String RELEASE = "stock.released";
    private static final String LOW_WARNING = "stock.low_warning";
    private static final String STOCK_CHANGED = "stock.changed";

    public void publishFlashSaleEvent(FlashSaleReservedEvent event)
    {
        kafkaTemplate.send(FLASH_SALE, event.getOrderId(), event);
    }

    public void publishReleaseEvent(ReleaseResponse response, List<ItemReserveRequest> itemsKafka, String reason) {
        ReleasePayload payload = new ReleasePayload(
                response.getOrderId(),
                reason,
                response.getReleasedAt(),
                itemsKafka
        );
        KafkaEvent<ReleasePayload> kafkaEvent = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                RELEASE,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(RELEASE, response.getOrderId(), kafkaEvent);
    }

    public void publishLowWarningEvent(Inventory inventory)
    {
        LowWarningPayload payload = new LowWarningPayload(
                inventory.getVariantId().toString(),
                inventory.getSku(),
                inventory.getStockQty(),
                inventory.getLowStockThreshold()
        );
        KafkaEvent<LowWarningPayload> kafkaEvent = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                LOW_WARNING,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(LOW_WARNING, payload.getVariantId(), kafkaEvent);
    }

    public void publishStockChangeEvent(List<String> variantIds)
    {
        KafkaEvent<StockChangePayload> kafkaEvent = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                STOCK_CHANGED,
                Instant.now().toString(),
                "1.0",
                new StockChangePayload(variantIds)
        );

        kafkaTemplate.send(STOCK_CHANGED,kafkaEvent);
    }
}
