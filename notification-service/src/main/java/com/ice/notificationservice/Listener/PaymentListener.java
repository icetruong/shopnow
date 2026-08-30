package com.ice.notificationservice.Listener;

import com.ice.notificationservice.DTO.Event.Consumer.KafkaEvent;
import com.ice.notificationservice.DTO.Event.Consumer.PaymentProcessedPayload;
import com.ice.notificationservice.DTO.Event.Consumer.PaymentRefundPayload;
import com.ice.notificationservice.Service.PaymentEventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentListener {
    private final ObjectMapper objectMapper;
    private final PaymentEventNotificationService paymentEventNotificationService;

    @KafkaListener(topics = "payment.processed", groupId = "notification-service")
    public void handleProcessed(String message)
    {
        KafkaEvent<PaymentProcessedPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<PaymentProcessedPayload>>() {});

        paymentEventNotificationService.onPaymentProcessed(kafkaEvent.getEventId(), kafkaEvent.getPayload());
    }

    @KafkaListener(topics = "payment.refunded", groupId = "notification-service")
    public void handleRefunded(String message)
    {
        KafkaEvent<PaymentRefundPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<PaymentRefundPayload>>() {});

        paymentEventNotificationService.onPaymentRefunded(kafkaEvent.getEventId(), kafkaEvent.getPayload());
    }
}
