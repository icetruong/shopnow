package com.ice.orderservice.Service;

import com.ice.orderservice.DTO.Event.Publish.*;
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
    private static final String ORDER_CONFIRMED = "order.confirmed";
    private static final String ORDER_REFUNDED = "order.refunded";

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

    public void publishOrderConfirmEvent(OrderConfirmPayload payload)
    {
        KafkaEvent<OrderConfirmPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                ORDER_CONFIRMED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(ORDER_CONFIRMED, payload.getOrderId(), event);
    }

    public void publishOrderRefundedEvent(OrderRefundedPayload payload)
    {
        KafkaEvent<OrderRefundedPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                ORDER_REFUNDED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(ORDER_REFUNDED, payload.getOrderId(), event);
    }
}
