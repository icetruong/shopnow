package com.ice.inventoryservice.Service;

import com.ice.inventoryservice.DTO.Event.FlashSaleReservedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String FLASH_SALE = "flash-sale-reserved";
    private static final String RESERVED = "stock.reserved";

    public void publishFlashSaleEvent(FlashSaleReservedEvent event)
    {
        kafkaTemplate.send(FLASH_SALE, event.getOrderId(), event);
    }

    public void publishReservedEvent()
}
