package com.ice.orderservice.Listener;

import com.ice.orderservice.Kafka.SafeConsumer;
import com.ice.orderservice.Service.StockReleaseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockEventListener {

    private final SafeConsumer safeConsumer;
    private final StockReleaseHandler handler;

    @KafkaListener(topics = "stock.released", groupId = "order-service")
    public void handleStockRelease(String message) {
        safeConsumer.run("stock.released", message, () -> handler.handle(message));
    }
}
