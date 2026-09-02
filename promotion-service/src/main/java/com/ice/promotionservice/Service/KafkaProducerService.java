package com.ice.promotionservice.Service;

import com.ice.promotionservice.DTO.Event.Publish.FlashPurchasedPayload;
import com.ice.promotionservice.DTO.Event.Publish.KafkaEvent;
import com.ice.promotionservice.DTO.Event.Publish.PromotionFlashSaleStartingPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String FLASH_PURCHASED = "flash.purchased";
    private static final String PROMOTION_FLASH_SALE_STARTING = "promotion.flash_sale_starting";

    public void publishFlashPurchased(FlashPurchasedPayload payload)
    {
        KafkaEvent<FlashPurchasedPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                FLASH_PURCHASED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(FLASH_PURCHASED, payload.getFlashSaleId(), event);
    }

    public void publishPromotionFlashSaleStarting(PromotionFlashSaleStartingPayload payload)
    {
        KafkaEvent<PromotionFlashSaleStartingPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                PROMOTION_FLASH_SALE_STARTING,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(PROMOTION_FLASH_SALE_STARTING, payload.getFlashSaleId(), event);
    }
}
