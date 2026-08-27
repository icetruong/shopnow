package com.ice.orderservice.Listener;

import com.ice.orderservice.DTO.Event.Cosume.StockReleasePayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.SagaStatus;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockEventListener {

    private final ObjectMapper objectMapper;
    private final OrderRepo orderRepo;
    private final SageStateRepo sageStateRepo;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(topics = "stock.released", groupId = "order-service")
    @Transactional
    public void handleStockRelease(String message)
    {
        KafkaEvent<StockReleasePayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<StockReleasePayload>>() {});

        StockReleasePayload payload = kafkaEvent.getPayload();
        if (!"RESERVATION_EXPIRED".equals(payload.getReason())) {
            log.info("stock.released reason={} không phải RESERVATION_EXPIRED, bỏ qua", payload.getReason());
            return;
        }
        Order order = orderRepo.findById(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order " + payload.getOrderId()));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} đã CANCELLED rồi, bỏ qua stock.released trùng lặp", order.getId());
            return;
        }

        SagaState sagaState = sageStateRepo.findByOrderId(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy saga cho order " + order.getId()));

        sagaState.setSagaStatus(SagaStatus.COMPENSATED);
        sageStateRepo.save(sagaState);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepo.save(order);

        kafkaProducerService.publishOrderCancelledEvent(new OrderCancelledPayload(
                order.getId().toString(),
                payload.getReason(),
                false,
                order.getOrderItems().stream()
                        .map(orderItem -> new OrderItemEvent(
                                orderItem.getVariantId().toString(),
                                orderItem.getQty()
                        )).toList()
        ));
    }
}
