package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.OrderCancelledPayload;
import com.ice.notificationservice.DTO.Event.Consumer.OrderConfirmPayload;
import com.ice.notificationservice.DTO.Event.Consumer.OrderCreatedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderListener {
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order.created", groupId = "notification-service")
    public void handleCreated(String message)
    {
        KafkaEvent<OrderCreatedPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<OrderCreatedPayload>>() {});

        OrderCreatedPayload payload = kafkaEvent.getPayload();

        // TODO: mai làm tiếp
    }

    @KafkaListener(topics = "order.confirmed", groupId = "notification-service")
    public void handleConfirmed(String message)
    {
        KafkaEvent<OrderConfirmPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<OrderConfirmPayload>>() {});

        OrderConfirmPayload payload = kafkaEvent.getPayload();

        // TODO: mai làm tiếp
    }

    @KafkaListener(topics = "order.cancelled", groupId = "notification-service")
    public void handleCancelled(String message)
    {
        KafkaEvent<OrderCancelledPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<OrderCancelledPayload>>() {});

        OrderCancelledPayload payload = kafkaEvent.getPayload();

        // TODO: mai làm tiếp
    }
}
