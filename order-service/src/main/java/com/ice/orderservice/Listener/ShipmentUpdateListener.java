package com.ice.orderservice.Listener;

import com.ice.orderservice.Kafka.SafeConsumer;
import com.ice.orderservice.Service.ShipmentUpdateHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ShipmentUpdateListener {
    private final SafeConsumer safeConsumer;
    private final ShipmentUpdateHandler handler;

    @KafkaListener(topics = "shipment.updated", groupId = "order-service")
    public void handleShipmentUpdate(String message) {
        safeConsumer.run("shipment.updated", message, () -> handler.handle(message));
    }
}
