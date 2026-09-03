package com.ice.orderservice.Listener;

import com.ice.orderservice.Kafka.SafeConsumer;
import com.ice.orderservice.Service.PaymentRefundHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRefundListener {

    private final SafeConsumer safeConsumer;
    private final PaymentRefundHandler handler;

    @KafkaListener(topics = "payment.refunded", groupId = "order-service")
    public void handlePaymentRefund(String message) {
        safeConsumer.run("payment.refunded", message, () -> handler.handle(message));
    }
}
