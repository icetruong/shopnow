package com.ice.orderservice.Service;

import com.ice.orderservice.DTO.Event.KafkaEvent;
import com.ice.orderservice.DTO.Event.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.OrderCreatedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String ORDER_CREATED = "order.created";
    private static final String ORDER_CANCELLED = "order.cancelled";

    public void publishOrderCreatedEvent(OrderCreatedPayload payload)
    {
        KafkaEvent<OrderCreatedPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                ORDER_CREATED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(ORDER_CREATED, payload.getOrderId(), event);
    }

    public void publishOrderCancelledEvent(OrderCancelledPayload payload)
    {
        KafkaEvent<OrderCancelledPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                ORDER_CANCELLED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(ORDER_CANCELLED, payload.getOrderId(), event);
    }
}
