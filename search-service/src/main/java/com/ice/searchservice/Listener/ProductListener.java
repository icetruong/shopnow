package com.ice.searchservice.Listener;

import com.ice.searchservice.DTO.Event.Consume.KafkaEvent;
import com.ice.searchservice.DTO.Event.Consume.ProductEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductListener {
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "product.updated", groupId = "search-service")
    public void handleUpdate(String message)
    {
        KafkaEvent<ProductEventPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<ProductEventPayload>>() {});

        ProductEventPayload payload = kafkaEvent.getPayload();

        // làm tiếp sau
    }
}
