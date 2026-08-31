package com.ice.searchservice.Listener;

import com.ice.searchservice.DTO.Event.Consume.KafkaEvent;
import com.ice.searchservice.DTO.Event.Consume.ProductEventPayload;
import com.ice.searchservice.Service.SearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductListener {
    private static final String PROCESSED_EVENT = "processed:event:";

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SearchSyncService productSyncService;

    @KafkaListener(topics = "product.updated", groupId = "search-service")
    public void handleUpdate(String message)
    {
        KafkaEvent<ProductEventPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<ProductEventPayload>>() {});

        ProductEventPayload payload = kafkaEvent.getPayload();

        if(Boolean.TRUE.equals(stringRedisTemplate.hasKey(PROCESSED_EVENT+kafkaEvent.getEventId())))
        {
            log.info("Event {} đã xử lý rồi, bỏ qua", kafkaEvent.getEventId());
            return;
        }
        if(Boolean.TRUE.equals(payload.getIsDeleted()))
            productSyncService.deleteProduct(payload.getProductId());
        else
            productSyncService.indexProduct(payload);

        stringRedisTemplate.opsForValue().set(PROCESSED_EVENT+kafkaEvent.getEventId(), "1", Duration.ofHours(24));
    }
}
