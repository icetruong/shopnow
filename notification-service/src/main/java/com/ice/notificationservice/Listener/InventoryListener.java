package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.LowWarningPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryListener {
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "stock.low_warning", groupId = "notification-service")
    public void handleLowWarning(String message)
    {
        KafkaEvent<LowWarningPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<LowWarningPayload>>() {});

        LowWarningPayload payload = kafkaEvent.getPayload();

        // TODO: mai làm tiếp
    }
}
