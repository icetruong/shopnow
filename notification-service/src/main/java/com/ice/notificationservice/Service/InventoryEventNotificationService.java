package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Event.Consumer.LowWarningPayload;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationDraft.Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * stock.low_warning — KHÁC các consumer khác: không có user cuối, gửi cảnh báo cho admin.
 * Bỏ RecipientResolver + preference gate. Admin lấy từ config; chưa cấu hình thì chỉ log.
 */
@Service
@Slf4j
public class InventoryEventNotificationService {

    private static final Template LOW_STOCK_INAPP = new Template(NotificationChannel.IN_APP,
            "Sắp hết hàng: {{sku}}",
            "SKU {{sku}} còn {{currentStock}} (ngưỡng {{threshold}}). Cần nhập thêm.");
    private static final Template LOW_STOCK_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Cảnh báo tồn kho thấp: {{sku}}",
            "SKU {{sku}} (variantId {{variantId}}) chỉ còn {{currentStock}}, dưới ngưỡng {{threshold}}.\n"
                    + "Vui lòng kiểm tra và nhập thêm hàng.");

    private final NotificationPipeline pipeline;
    private final String adminUserId;
    private final String adminEmail;

    public InventoryEventNotificationService(
            NotificationPipeline pipeline,
            @Value("${notification.admin.user-id:}") String adminUserId,
            @Value("${notification.admin.email:}") String adminEmail) {
        this.pipeline = pipeline;
        this.adminUserId = adminUserId;
        this.adminEmail = adminEmail;
    }

    public void onLowStockWarning(String eventId, LowWarningPayload p) {
        if (adminUserId == null || adminUserId.isBlank()) {
            log.warn("stock.low_warning: chưa cấu hình notification.admin.user-id -> chỉ log. "
                    + "sku={} còn={} ngưỡng={}", p.getSku(), p.getCurrentStock(), p.getThreshold());
            return;
        }
        if (pipeline.alreadyProcessed(eventId)) return;

        Map<String, Object> vars = Map.of(
                "sku", p.getSku(),
                "variantId", p.getVariantId(),
                "currentStock", p.getCurrentStock(),
                "threshold", p.getThreshold());

        pipeline.deliverAlways(eventId, NotificationType.SYSTEM, UUID.fromString(adminUserId),
                "/admin/inventory",
                List.of(LOW_STOCK_INAPP.render(null, vars),
                        LOW_STOCK_EMAIL.render(adminEmail, vars)));
    }
}
