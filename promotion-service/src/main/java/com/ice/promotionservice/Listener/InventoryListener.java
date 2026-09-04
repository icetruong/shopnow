package com.ice.promotionservice.Listener;

import com.ice.promotionservice.DTO.Event.Consume.FlashPurchasedPayload;
import com.ice.promotionservice.DTO.Event.Publish.KafkaEvent;
import com.ice.promotionservice.Service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryListener {
    private final ObjectMapper objectMapper;
    private final PromotionService promotionService;

    @KafkaListener(topics = "flash.purchased", groupId = "promotion-service")
    public void handlePurchased(String message)
    {
        KafkaEvent<FlashPurchasedPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<FlashPurchasedPayload>>() {});

        FlashPurchasedPayload payload = kafkaEvent.getPayload();

        promotionService.purchaseFlashSale(payload, kafkaEvent.getEventId());
    }
}
