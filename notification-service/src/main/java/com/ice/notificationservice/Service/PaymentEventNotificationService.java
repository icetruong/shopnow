package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Event.Consumer.PaymentProcessedPayload;
import com.ice.notificationservice.DTO.Event.Consumer.PaymentRefundPayload;
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
public class PaymentEventNotificationService {

    private static final Template PAID_INAPP = new Template(NotificationChannel.IN_APP,
            "Thanh toán đơn {{orderCode}} thành công",
            "Đã nhận {{amount}}đ cho đơn {{orderCode}} qua {{method}}.");
    private static final Template PAID_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Biên nhận thanh toán đơn {{orderCode}}",
            "Chào {{fullName}},\nShopNow đã nhận {{amount}}đ cho đơn {{orderCode}} "
                    + "(phương thức: {{method}}).\nCảm ơn bạn đã mua sắm.");

    private static final Template FAILED_INAPP = new Template(NotificationChannel.IN_APP,
            "Thanh toán đơn {{orderCode}} thất bại",
            "Thanh toán {{amount}}đ cho đơn {{orderCode}} không thành công. Vui lòng thử lại.");
    private static final Template FAILED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Thanh toán đơn {{orderCode}} không thành công",
            "Chào {{fullName}},\nThanh toán cho đơn {{orderCode}} không thành công. "
                    + "Bạn có thể thử thanh toán lại trong chi tiết đơn hàng.");

    private static final Template REFUNDED_INAPP = new Template(NotificationChannel.IN_APP,
            "Đã hoàn tiền đơn {{orderCode}}",
            "Đã hoàn {{amount}}đ cho đơn {{orderCode}}. Tiền về tài khoản trong 3-5 ngày làm việc.");
    private static final Template REFUNDED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Xác nhận hoàn tiền đơn {{orderCode}}",
            "Chào {{fullName}},\nShopNow đã hoàn {{amount}}đ cho đơn {{orderCode}}. "
                    + "Tiền sẽ về trong 3-5 ngày làm việc tùy ngân hàng.");

    private final NotificationPipeline pipeline;
    private final RecipientResolver recipientResolver;

    /** payload.status = SUCCESS | FAILED -> chọn khuôn tương ứng. */
    public void onPaymentProcessed(String eventId, PaymentProcessedPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        boolean success = "SUCCESS".equalsIgnoreCase(p.getStatus());

        RecipientResolver.Resolved resolved = recipientResolver.byOrderId(p.getOrderId());
        Recipient r = resolved.recipient();
        Map<String, Object> vars = Map.of(
                "orderCode", resolved.order().getOrderCode(),
                "amount", Money.vnd(p.getAmount()),
                "method", p.getMethod() != null ? p.getMethod() : "",
                "fullName", r.displayName());

        Template inApp = success ? PAID_INAPP : FAILED_INAPP;
        Template email = success ? PAID_EMAIL : FAILED_EMAIL;

        pipeline.deliver(eventId, NotificationType.PAYMENT, r.userId(), actionUrl(p.getOrderId()),
                List.of(inApp.render(null, vars),
                        email.render(r.email(), vars)));
    }

    public void onPaymentRefunded(String eventId, PaymentRefundPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        RecipientResolver.Resolved resolved = recipientResolver.byOrderId(p.getOrderId());
        Recipient r = resolved.recipient();
        Map<String, Object> vars = Map.of(
                "orderCode", resolved.order().getOrderCode(),
                "amount", Money.vnd(p.getAmount()),
                "fullName", r.displayName());

        pipeline.deliver(eventId, NotificationType.PAYMENT, r.userId(), actionUrl(p.getOrderId()),
                List.of(REFUNDED_INAPP.render(null, vars),
                        REFUNDED_EMAIL.render(r.email(), vars)));
    }

    private static String actionUrl(String orderId) {
        return "/orders/" + orderId;
    }
}
