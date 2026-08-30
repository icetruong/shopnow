package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.ShipmentUpdatePayload;
import com.ice.notificationservice.Service.ShipmentEventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class ShipmentListener {
    private final ObjectMapper objectMapper;
    private final ShipmentEventNotificationService shipmentEventNotificationService;

    @KafkaListener(topics = "shipment.updated", groupId = "notification-service")
    public void handleUpdate(String message)
    {
        KafkaEvent<ShipmentUpdatePayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<ShipmentUpdatePayload>>() {});

        shipmentEventNotificationService.onShipmentUpdated(kafkaEvent.getEventId(), kafkaEvent.getPayload());
    }
}
