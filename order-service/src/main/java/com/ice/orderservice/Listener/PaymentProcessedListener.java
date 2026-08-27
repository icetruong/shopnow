package com.ice.orderservice.Listener;

import com.ice.orderservice.Client.InventoryClient;
import com.ice.orderservice.DTO.Event.Cosume.PaymentProcessedPayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderConfirmPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import com.ice.orderservice.DTO.Event.Publish.ShippingAddressEvent;
import com.ice.orderservice.DTO.Request.Inventory.DeductRequest;
import com.ice.orderservice.DTO.Request.Inventory.ReleaseRequest;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.OrderShippingAddress;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.*;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderRepo;
import com.ice.orderservice.Repository.OrderShippingAddressRepo;
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
public class PaymentProcessedListener {

    private final ObjectMapper objectMapper;
    private final OrderRepo orderRepo;
    private final OrderShippingAddressRepo orderShippingAddressRepo;
    private final SageStateRepo sageStateRepo;
    private final InventoryClient inventoryClient;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(topics = "payment.processed", groupId = "order-service")
    @Transactional
    public void handlePaymentProcessed(String message)
    {
        KafkaEvent<PaymentProcessedPayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<PaymentProcessedPayload>>() {});

        PaymentProcessedPayload payload = kafkaEvent.getPayload();

        Order order = orderRepo.findById(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order " + payload.getOrderId()));

        SagaState sagaState = sageStateRepo.findByOrderId(UUID.fromString(payload.getOrderId()))
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy saga cho order " + order.getId()));

        if (order.getStatus() == OrderStatus.CONFIRMED || order.getStatus() == OrderStatus.CANCELLED) {
            log.info("Order {} đã xử lý payment.processed rồi (status={}), bỏ qua", order.getId(), order.getStatus());
            return;
        }

        if(payload.getStatus() == PaymentGatewayStatus.SUCCESS)
        {
            sagaState.getCompletedSteps().add("PAYMENT_PROCESSED");
            sagaState.setCurrentStep(CurrentStep.PAYMENT_PROCESSED);
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setTransactionId(payload.getTransactionId());

            inventoryClient.deduct(new DeductRequest(order.getId().toString()));

            sagaState.getCompletedSteps().add("STOCK_DEDUCTED");
            sagaState.getCompletedSteps().add("ORDER_CONFIRMED");
            sagaState.setCurrentStep(CurrentStep.ORDER_CONFIRMED);
            order.setStatus(OrderStatus.CONFIRMED);
            sagaState.setSagaStatus(SagaStatus.COMPLETED);

            orderRepo.save(order);
            sageStateRepo.save(sagaState);

            OrderShippingAddress shippingAddress = orderShippingAddressRepo.findByOrderId(order.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ giao hàng"));

            kafkaProducerService.publishOrderConfirmEvent(new OrderConfirmPayload(
                    order.getId().toString(),
                    order.getOrderCode(),
                    order.getUserId().toString(),
                    new ShippingAddressEvent(
                            shippingAddress.getFullName(),
                            shippingAddress.getPhone(),
                            shippingAddress.getProvince(),
                            shippingAddress.getDistrict(),
                            shippingAddress.getWard(),
                            shippingAddress.getStreetDetail()
                    ),
                    order.getOrderItems().stream()
                            .map(item -> new OrderItemEvent(item.getVariantId().toString(), item.getQty()))
                            .toList()
            ));
        }
        else if (payload.getStatus() == PaymentGatewayStatus.FAILED)
        {
            sagaState.setSagaStatus(SagaStatus.COMPENSATED);

            inventoryClient.release(new ReleaseRequest(order.getId().toString(), ReasonRelease.PAYMENT_FAILED));

            order.setStatus(OrderStatus.CANCELLED);

            orderRepo.save(order);
            sageStateRepo.save(sagaState);

            kafkaProducerService.publishOrderCancelledEvent(new OrderCancelledPayload(
                    order.getId().toString(),
                    "PAYMENT_FAILED",
                    false,
                    order.getOrderItems().stream()
                            .map(orderItem ->
                                    new OrderItemEvent(
                                            orderItem.getVariantId().toString(), orderItem.getQty())
                            )
                            .toList()
            ));
        }
    }
}
