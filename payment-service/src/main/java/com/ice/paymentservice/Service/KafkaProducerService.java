package com.ice.paymentservice.Service;

import com.ice.paymentservice.DTO.Event.KafkaEvent;
import com.ice.paymentservice.DTO.Event.PaymentProcessedPayload;
import com.ice.paymentservice.DTO.Event.PaymentRefundPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String PAYMENT_REFUNDED = "payment.refunded";
    private static final String PAYMENT_PROCESSED = "payment.processed";

    public void publishProcessedPaymentEvent(PaymentProcessedPayload payload)
    {
        KafkaEvent<PaymentProcessedPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                PAYMENT_PROCESSED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(PAYMENT_PROCESSED, payload.getPaymentId(), event);
    }

    public void publishRefundedPaymentEvent(PaymentRefundPayload payload)
    {
        KafkaEvent<PaymentRefundPayload> event = new KafkaEvent<>(
                UUID.randomUUID().toString(),
                PAYMENT_REFUNDED,
                Instant.now().toString(),
                "1.0",
                payload
        );

        kafkaTemplate.send(PAYMENT_REFUNDED, payload.getRefundId(), event);
    }
}
