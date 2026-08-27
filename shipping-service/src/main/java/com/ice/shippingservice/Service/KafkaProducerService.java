package com.ice.shippingservice.Service;

import com.ice.shippingservice.DTO.Event.Publish.KafkaEvent;
import com.ice.shippingservice.DTO.Event.Publish.ShipmentUpdatePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final static String SHIPMENT_UPDATE = "shipment.updated";

    public void publishShipmentUpdate(ShipmentUpdatePayload payload)
    {
        KafkaEvent<ShipmentUpdatePayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                SHIPMENT_UPDATE,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(SHIPMENT_UPDATE, payload.getShipmentId(), event);
    }
}
