package com.ice.shippingservice.Listener;

import com.ice.shippingservice.DTO.Event.Consume.OrderCancelledPayload;
import com.ice.shippingservice.DTO.Event.Publish.KafkaEvent;
import com.ice.shippingservice.Service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderCancelledListener {
    private final ObjectMapper objectMapper;
    private final ShippingService shippingService;

    @KafkaListener(topics = "order.cancelled", groupId = "shipping-service")
    public void handleCancel(String message)
    {
        KafkaEvent<OrderCancelledPayload> kafkaEvent
                = objectMapper.readValue(message, new TypeReference<KafkaEvent<OrderCancelledPayload>>() {});

        OrderCancelledPayload orderCancelledPayload = kafkaEvent.getPayload();

        shippingService.orderCancelled(orderCancelledPayload, kafkaEvent.getEventId());
    }
}
