package com.ice.orderservice.Listener;

import com.ice.orderservice.Kafka.SafeConsumer;
import com.ice.orderservice.Service.PaymentProcessedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentProcessedListener {

    private final SafeConsumer safeConsumer;
    private final PaymentProcessedHandler handler;

    @KafkaListener(topics = "payment.processed", groupId = "order-service")
    public void handlePaymentProcessed(String message) {
        safeConsumer.run("payment.processed", message, () -> handler.handle(message));
    }
}
