package com.ice.orderservice.Listener;

import com.ice.orderservice.DTO.Event.Cosume.PaymentRefundPayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.PaymentStatus;
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
public class PaymentRefundListener {

    private final ObjectMapper objectMapper;
    private final OrderRepo orderRepo;
    private final SageStateRepo sageStateRepo;

    @KafkaListener(topics = "payment.refunded", groupId = "order-service")
    @Transactional
    public void handlePaymentRefund(String message)
    {
        KafkaEvent<PaymentRefundPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<PaymentRefundPayload>>() {});

        PaymentRefundPayload payload = kafkaEvent.getPayload();

        Order order = orderRepo.findById(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order " + payload.getOrderId()));

        if(order.getStatus() == OrderStatus.REFUNDED)
        {
            log.info("Order {} đã REFUNDED rồi, bỏ qua payment.refunded trùng lặp", order.getId());
            return;
        }

        SagaState sagaState = sageStateRepo.findByOrderId(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy saga cho order " + order.getId()));

        sagaState.setSagaStatus(SagaStatus.COMPENSATED);
        sageStateRepo.save(sagaState);

        order.setStatus(OrderStatus.REFUNDED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        orderRepo.save(order);

        // TODO: publish notification hoàn tiền"
    }
}
