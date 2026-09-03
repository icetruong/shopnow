package com.ice.orderservice.Service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Thân xử lý stock.released. Tách khỏi StockEventListener để @Transactional chạy qua proxy
 * và rollback trước khi SafeConsumer log & nuốt lỗi.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StockReleaseHandler {

    private final ObjectMapper objectMapper;
    private final OrderRepo orderRepo;
    private final SageStateRepo sageStateRepo;
    private final KafkaProducerService kafkaProducerService;
    private final IdempotencyService idempotencyService;

    @Transactional
    public void handle(String message)
    {
        KafkaEvent<StockReleasePayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<StockReleasePayload>>() {});

        StockReleasePayload payload = kafkaEvent.getPayload();
        if (!"RESERVATION_EXPIRED".equals(payload.getReason())) {
            log.info("stock.released reason={} không phải RESERVATION_EXPIRED, bỏ qua", payload.getReason());
            return;
        }

        if(idempotencyService.isProcessed(kafkaEvent.getEventId()))
            return;
        Order order = orderRepo.findByIdForUpdate(UUID.fromString(payload.getOrderId()))   // 1.5: khóa dòng khi sửa
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

        idempotencyService.markProcessed(kafkaEvent.getEventId());
    }
}
