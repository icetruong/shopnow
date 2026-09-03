package com.ice.orderservice.Service;

import com.ice.orderservice.Client.PaymentClient;
import com.ice.orderservice.DTO.Event.Cosume.ShipmentUpdatePayload;
import com.ice.orderservice.DTO.Event.Publish.KafkaEvent;
import com.ice.orderservice.DTO.Response.Payment.PaymentInternalResponse;
import com.ice.orderservice.Entity.Order;
import com.ice.orderservice.Entity.OrderStatusHistory;
import com.ice.orderservice.Enum.OrderStatus;
import com.ice.orderservice.Enum.PaymentMethod;
import com.ice.orderservice.Exception.ResourceNotFoundException;
import com.ice.orderservice.Repository.OrderRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/**
 * Thân xử lý {@code shipment.updated} (từ Shipping Service).
 *
 * Đẩy {@code order.status} đi tiếp trên nhánh ngang CONFIRMED → PROCESSING → SHIPPING → DELIVERED,
 * song song với PATCH /admin/orders/{id}/status. Quy tắc (orderServiceApiSpec.md "Tiến trình logistics"):
 *  - Chỉ áp dụng cho đơn ĐÃ ở trong vòng đời logistics; KHÔNG đụng saga_state (saga đã COMPLETED từ CONFIRMED).
 *  - Chỉ chấp nhận transition ĐÚNG CHIỀU. Event trễ / trùng / lùi bước -> bỏ qua, không lỗi.
 *  - Không bao giờ suy ra CANCELLED / REFUNDING từ event này.
 *  - order_status_history.changed_by = "SYSTEM".
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShipmentUpdateHandler {

    private static final String CHANGED_BY_SYSTEM = "SYSTEM";

    // Chuỗi tiến trình logistics ngang. "Đúng chiều" = index(to) > index(from).
    // NOTE: OrderService.ALLOWED_STATUS_TRANSITIONS mô tả cùng nhánh này (dạng one-step);
    // ở đây cho phép nhảy tiến nhiều bước để bền với event bị mất, vẫn chặn lùi/trùng.
    private static final List<OrderStatus> LOGISTICS_FLOW = List.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PROCESSING,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED
    );

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final OrderRepo orderRepo;
    private final PaymentClient paymentClient;

    @Transactional
    public void handle(String message) {
        KafkaEvent<ShipmentUpdatePayload> kafkaEvent =
                objectMapper.readValue(message, new TypeReference<KafkaEvent<ShipmentUpdatePayload>>() {});

        ShipmentUpdatePayload payload = kafkaEvent.getPayload();

        if (idempotencyService.isProcessed(kafkaEvent.getEventId())) {
            return;
        }

        Order order = orderRepo.findByIdForUpdate(UUID.fromString(payload.getOrderId()))   // 1.5: khóa dòng khi sửa
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy order " + payload.getOrderId()));

        OrderStatus current = order.getStatus();

        // Chỉ xử lý đơn đang trong vòng đời logistics (đã qua CONFIRMED, chưa CANCELLED/REFUND...).
        if (!LOGISTICS_FLOW.contains(current)) {
            log.info("shipment.updated (order={}, status={}) — order ngoài vòng logistics, bỏ qua",
                    order.getId(), current);
            return;
        }

        switch (payload.getStatus()) {
            case READY_TO_PICK -> advance(order, OrderStatus.PROCESSING, payload);
            case PICKED_UP -> advance(order, OrderStatus.SHIPPING, payload);
            case DELIVERED -> {
                boolean moved = advance(order, OrderStatus.DELIVERED, payload);
                if (moved && order.getPaymentMethod() == PaymentMethod.COD) {
                    confirmCodSafely(order);
                }
            }
            case IN_TRANSIT -> {
                // Không đổi status, chỉ ghi một mốc timeline.
                writeHistory(order, current, current, descOr(payload, "Đang vận chuyển"));
                log.info("shipment IN_TRANSIT (order={}, tracking={})", order.getId(), payload.getTrackingCode());
            }
            case FAILED, RETURNED -> {
                // Giữ nguyên status; ghi history + cảnh báo để admin xử lý tay (re-ship / hoàn tiền).
                writeHistory(order, current, current,
                        "Vận đơn " + payload.getStatus() + ": " + descOr(payload, "(không có mô tả)"));
                log.warn("[SHIPMENT-{}] order={} shipment={} — cần admin xử lý tay",
                        payload.getStatus(), order.getId(), payload.getShipmentId());
            }
            case CANCELLED ->
                    log.info("shipment.updated CANCELLED (order={}) — bỏ qua, order tự hủy qua luồng riêng", order.getId());
            case PENDING ->
                    log.info("shipment.updated PENDING (order={}) — chưa có gì để làm", order.getId());
        }

        orderRepo.save(order);
        idempotencyService.markProcessed(kafkaEvent.getEventId());
    }

    /** Đẩy order tới {@code target} nếu đó là bước TIẾN hợp lệ. Trả về true nếu thực sự đổi status. */
    private boolean advance(Order order, OrderStatus target, ShipmentUpdatePayload payload) {
        OrderStatus current = order.getStatus();
        if (!isForward(current, target)) {
            log.info("shipment {} (order={}, status={}) không phải bước tiến tới {} — bỏ qua",
                    payload.getStatus(), order.getId(), current, target);
            return false;
        }
        order.setStatus(target);
        writeHistory(order, current, target, descOr(payload, "Cập nhật từ vận đơn: " + payload.getStatus()));
        log.info("order {} {} -> {} (shipment {})", order.getId(), current, target, payload.getStatus());
        return true;
    }

    private boolean isForward(OrderStatus from, OrderStatus to) {
        int i = LOGISTICS_FLOW.indexOf(from);
        int j = LOGISTICS_FLOW.indexOf(to);
        return i >= 0 && j > i;
    }

    private void writeHistory(Order order, OrderStatus from, OrderStatus to, String note) {
        order.getOrderStatusHistories().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(from)
                .toStatus(to)
                .note(note)
                .changedBy(CHANGED_BY_SYSTEM)
                .build());
    }

    /**
     * DELIVERED + COD: báo payment-service đã thu tiền. Lỗi ở đây KHÔNG được làm rớt việc
     * "đơn đã giao" — chỉ log để đối soát tay (giao hàng là sự thật vật lý, không rollback).
     */
    private void confirmCodSafely(Order order) {
        try {
            PaymentInternalResponse payment = paymentClient.getPaymentByOrderId(order.getId().toString());
            paymentClient.confirmCod(payment.getPaymentId());
        } catch (Exception e) {
            log.error("confirm-cod thất bại cho order {} (đã DELIVERED) — cần đối soát COD thủ công", order.getId(), e);
        }
    }

    private String descOr(ShipmentUpdatePayload payload, String fallback) {
        String d = payload.getDescription();
        return (d == null || d.isBlank()) ? fallback : d;
    }
}
