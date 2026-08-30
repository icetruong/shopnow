package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Event.Consumer.OrderCancelledPayload;
import com.ice.notificationservice.DTO.Event.Consumer.OrderConfirmPayload;
import com.ice.notificationservice.DTO.Event.Consumer.OrderCreatedPayload;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationDraft.Template;
import com.ice.notificationservice.Service.RecipientResolver.Recipient;
import com.ice.notificationservice.Util.Money;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventNotificationService {

    private static final Template CREATED_INAPP = new Template(NotificationChannel.IN_APP,
            "Đơn hàng {{orderCode}} đã được tạo",
            "Đơn {{orderCode}} trị giá {{totalAmount}}đ đang chờ xác nhận.");
    private static final Template CREATED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Đã nhận đơn hàng {{orderCode}}",
            "Chào {{fullName}},\nShopNow đã nhận đơn {{orderCode}} ({{totalAmount}}đ).");

    private static final Template CONFIRMED_INAPP = new Template(NotificationChannel.IN_APP,
            "Đơn hàng {{orderCode}} đã được xác nhận",
            "Đơn {{orderCode}} đã được xác nhận và đang chuẩn bị hàng.");
    private static final Template CONFIRMED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Đơn hàng {{orderCode}} đã được xác nhận",
            "Chào {{fullName}},\nĐơn {{orderCode}} của bạn đã được xác nhận và đang được đóng gói.");

    private static final Template CANCELLED_INAPP = new Template(NotificationChannel.IN_APP,
            "Đơn hàng {{orderCode}} đã bị hủy",
            "Đơn {{orderCode}} đã bị hủy. Lý do: {{reason}}.");
    private static final Template CANCELLED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Đơn hàng {{orderCode}} đã bị hủy",
            "Chào {{fullName}},\nĐơn {{orderCode}} đã bị hủy. Lý do: {{reason}}.\n"
                    + "Nếu bạn đã thanh toán, tiền sẽ được hoàn trong 3-5 ngày làm việc.");

    private final NotificationPipeline pipeline;
    private final RecipientResolver recipientResolver;

    /** order.created — payload đã có userId + orderCode. */
    public void onOrderCreated(String eventId, OrderCreatedPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        Recipient r = recipientResolver.byUserId(p.getUserId());
        Map<String, Object> vars = Map.of(
                "orderCode", p.getOrderCode(),
                "totalAmount", Money.vnd(p.getTotalAmount()),
                "fullName", r.displayName());

        pipeline.deliver(eventId, NotificationType.ORDER, r.userId(), actionUrl(p.getOrderId()),
                List.of(CREATED_INAPP.render(null, vars),
                        CREATED_EMAIL.render(r.email(), vars)));
    }

    /** order.confirmed — payload đã có userId + orderCode. */
    public void onOrderConfirmed(String eventId, OrderConfirmPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        Recipient r = recipientResolver.byUserId(p.getUserId());
        Map<String, Object> vars = Map.of(
                "orderCode", p.getOrderCode(),
                "fullName", r.displayName());

        pipeline.deliver(eventId, NotificationType.ORDER, r.userId(), actionUrl(p.getOrderId()),
                List.of(CONFIRMED_INAPP.render(null, vars),
                        CONFIRMED_EMAIL.render(r.email(), vars)));
    }

    /** order.cancelled — payload chỉ có orderId + reason -> phải hỏi Order Service. */
    public void onOrderCancelled(String eventId, OrderCancelledPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        RecipientResolver.Resolved resolved = recipientResolver.byOrderId(p.getOrderId());
        Recipient r = resolved.recipient();
        Map<String, Object> vars = Map.of(
                "orderCode", resolved.order().getOrderCode(),
                "reason", reasonText(p.getReason()),
                "fullName", r.displayName());

        pipeline.deliver(eventId, NotificationType.ORDER, r.userId(), actionUrl(p.getOrderId()),
                List.of(CANCELLED_INAPP.render(null, vars),
                        CANCELLED_EMAIL.render(r.email(), vars)));
    }

    private static String actionUrl(String orderId) {
        return "/orders/" + orderId;
    }

    private static String reasonText(String code) {
        return switch (code == null ? "" : code) {
            case "PAYMENT_FAILED" -> "Thanh toán không thành công";
            case "OUT_OF_STOCK"   -> "Sản phẩm hết hàng";
            case "USER_CANCELLED" -> "Bạn đã yêu cầu hủy";
            default               -> "Không xác định";
        };
    }
}
