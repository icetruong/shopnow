package com.ice.orderservice.Scheduler;

import com.ice.orderservice.DTO.Event.Publish.OrderCancelledPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderConfirmPayload;
import com.ice.orderservice.DTO.Event.Publish.OrderItemEvent;
import com.ice.orderservice.DTO.Event.Publish.ShippingAddressEvent;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.OrderShippingAddress;
import com.ice.orderservice.Entity.SagaState;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.SagaStatus;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderShippingAddressRepo;
import com.ice.orderservice.Repository.SageStateRepo;
import com.ice.orderservice.Service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SagaStateScheduler {
    private static final int STUCK_MINUTES = 10;   // saga không nhúc nhích 10 phút = nghi kẹt
    private static final int MAX_RETRY = 5;
    private static final int BATCH_SIZE = 50;

    private final SageStateRepo sageStateRepo;
    private final OrderShippingAddressRepo orderShippingAddressRepo;
    private final KafkaProducerService kafkaProducerService;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void recoverStuckSagas()
    {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_MINUTES);

        List<SagaState> sagaStates = sageStateRepo.findStuck(
                List.of(SagaStatus.STARTED, SagaStatus.COMPENSATING),
                threshold,
                PageRequest.of(0, BATCH_SIZE)
        );

        for (SagaState saga : sagaStates) {
            try {
                recoverOne(saga);
            } catch (Exception ex) {
                log.error("Recovery lỗi cho saga {} (order {})",
                        saga.getId(), saga.getOrder().getId(), ex);
            }
        }

    }

    private void recoverOne(SagaState saga) {
        Order order = saga.getOrder();

        if(saga.getRetryCount() >= MAX_RETRY)
        {
            saga.setSagaStatus(SagaStatus.FAILED);
            saga.setFailureReason("Recovery quá " + MAX_RETRY + " lần vẫn kẹt ở "
                    + saga.getCurrentStep() + " / " + saga.getSagaStatus());
            log.error("[SAGA-FAILED] order={} step={} -- cần xử lý tay",
                    order.getId(), saga.getCurrentStep());
            return;
        }

        saga.setRetryCount(saga.getRetryCount()+1);

        if(saga.getSagaStatus() == SagaStatus.COMPENSATING)
        {
            compensate(order, saga, "RESUME_COMPENSATION");
            return;
        }

        switch (saga.getCurrentStep())
        {
            case ORDER_CREATED, STOCK_RESERVED ->
                    compensate(order, saga, "SAGA_TIMEOUT_" + saga.getCurrentStep());

            case PAYMENT_PROCESSED -> {
                // Tiền có thể đã bị trừ mà saga chưa hoàn tất -> KHÔNG tự huỷ, báo người.
                saga.setSagaStatus(SagaStatus.FAILED);
                saga.setFailureReason("Kẹt ở PAYMENT_PROCESSED - nghi đã trừ tiền, cần đối soát payment-service");
                log.error("[SAGA-FAILED] order={} kẹt sau khi payment xử lý -- cần đối soát tiền", order.getId());
            }

            case STOCK_DEDUCTED, ORDER_CONFIRMED -> {
                // Nghiệp vụ đã xong, chỉ event/status chưa chốt -> re-publish + đóng saga.
                if (order.getStatus() != OrderStatus.CONFIRMED) {
                    order.setStatus(OrderStatus.CONFIRMED);
                }

                OrderShippingAddress shippingAddress = orderShippingAddressRepo.findByOrderId(order.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Không tìm thấy địa chỉ giao hàng cho order " + order.getId()));

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
                        order.getOrderItems().stream().map(
                                orderItem -> new OrderItemEvent(
                                        orderItem.getVariantId().toString(),
                                        orderItem.getQty()
                                )
                        ).toList()
                ));
                saga.setSagaStatus(SagaStatus.COMPLETED);
                log.warn("Recovery: order {} re-publish order.confirmed, đóng saga COMPLETED", order.getId());
            }
        }
    }

    private void compensate(Order order, SagaState saga, String reason)
    {
        order.setStatus(OrderStatus.CANCELLED);
        saga.setSagaStatus(SagaStatus.COMPENSATED);
        kafkaProducerService.publishOrderCancelledEvent(new OrderCancelledPayload(
                order.getId().toString(),
                reason,
                false,
                order.getOrderItems().stream().map(
                        orderItem -> new OrderItemEvent(
                                orderItem.getVariantId().toString(),
                                orderItem.getQty()
                        )
                ).toList()
        ));
        log.warn("Recovery: order {} kẹt ở {} -> huỷ đơn + publish order.cancelled (reason={})",
                order.getId(), saga.getCurrentStep(), reason);
    }
}
