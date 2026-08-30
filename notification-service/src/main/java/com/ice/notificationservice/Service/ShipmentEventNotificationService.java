package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Event.Consumer.ShipmentUpdatePayload;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationDraft.Template;
import com.ice.notificationservice.Service.RecipientResolver.Recipient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventNotificationService {

    private static final Template UPDATED_INAPP = new Template(NotificationChannel.IN_APP,
            "Đơn {{orderCode}}: {{statusText}}",
            "{{description}} (Đơn vị: {{carrier}}, mã vận đơn {{trackingCode}}).");
    // SMS: nội dung (body) viết không dấu để không bị tính 2-3 lần độ dài.
    // title chỉ là metadata cho bảng notifications (SMS thực tế chỉ gửi body).
    private static final Template UPDATED_SMS = new Template(NotificationChannel.SMS,
            "Cập nhật vận chuyển đơn {{orderCode}}",
            "ShopNow: don {{orderCode}} - {{statusText}}. Ma van don {{trackingCode}}.");
    private static final Template UPDATED_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Cập nhật vận chuyển đơn {{orderCode}}",
            "Chào {{fullName}},\nĐơn {{orderCode}}: {{statusText}}.\n{{description}}\n"
                    + "Đơn vị vận chuyển: {{carrier}} — mã vận đơn: {{trackingCode}}.\n"
                    + "Dự kiến giao: {{estimatedDate}}.");

    private final NotificationPipeline pipeline;
    private final RecipientResolver recipientResolver;

    /** shipment.updated — payload chỉ có orderId -> phải hỏi Order Service. */
    public void onShipmentUpdated(String eventId, ShipmentUpdatePayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        RecipientResolver.Resolved resolved = recipientResolver.byOrderId(p.getOrderId());
        Recipient r = resolved.recipient();
        Map<String, Object> vars = Map.of(
                "orderCode", resolved.order().getOrderCode(),
                "statusText", statusText(p.getStatus()),
                "carrier", nullToEmpty(p.getCarrier()),
                "trackingCode", nullToEmpty(p.getTrackingCode()),
                "description", nullToEmpty(p.getDescription()),
                "estimatedDate", p.getEstimatedDate() != null ? p.getEstimatedDate().toString() : "",
                "fullName", r.displayName());

        pipeline.deliver(eventId, NotificationType.SHIPMENT, r.userId(), "/orders/" + p.getOrderId(),
                List.of(UPDATED_INAPP.render(null, vars),
                        UPDATED_SMS.render(r.phone(), vars),
                        UPDATED_EMAIL.render(r.email(), vars)));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String statusText(String code) {
        return switch (code == null ? "" : code) {
            case "READY_TO_PICK"    -> "Đơn đã sẵn sàng, chờ lấy hàng";
            case "PICKED_UP"        -> "Đã lấy hàng";
            case "IN_TRANSIT"       -> "Đang vận chuyển";
            case "OUT_FOR_DELIVERY" -> "Đang giao đến bạn";
            case "DELIVERED"        -> "Giao hàng thành công";
            case "FAILED"           -> "Giao hàng không thành công";
            case "RETURNED"         -> "Đơn hàng đã hoàn trả";
            case "CANCELLED"        -> "Vận đơn đã hủy";
            default                 -> code == null || code.isBlank() ? "Cập nhật vận chuyển" : code;
        };
    }
}
