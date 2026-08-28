package com.ice.shippingservice.Listener;

import com.ice.shippingservice.DTO.Event.Consume.OrderConfirmPayload;
import com.ice.shippingservice.DTO.Event.Publish.KafkaEvent;
import com.ice.shippingservice.DTO.Response.Order.OrderDetailResponse;
import com.ice.shippingservice.Service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OrderConfirmListener {
    private final ObjectMapper objectMapper;              // tools.jackson.databind.ObjectMapper
    private final ShippingService shippingService;

    @KafkaListener(topics = "order.confirmed", groupId = "shipping-service")
    public void handle(String message)
    {
        KafkaEvent<OrderConfirmPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<OrderConfirmPayload>>() {});

        OrderConfirmPayload orderConfirmPayload = kafkaEvent.getPayload();

        shippingService.createFromOrderConfirmed(orderConfirmPayload, kafkaEvent.getEventId());
    }
}
