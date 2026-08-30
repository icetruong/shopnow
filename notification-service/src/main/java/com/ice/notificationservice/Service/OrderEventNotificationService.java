package com.ice.notificationservice.Service;

import com.ice.notificationservice.Client.UserClient;
import com.ice.notificationservice.DTO.Event.Consumer.OrderCreatedPayload;
import com.ice.notificationservice.DTO.Response.User.InternalUserResponse;
import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Repository.NotificationRepo;
import com.ice.notificationservice.Util.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventNotificationService {
    private static final String INAPP_TITLE = "Đơn hàng {{orderCode}} đã được tạo";
    private static final String INAPP_BODY  = "Đơn {{orderCode}} trị giá {{totalAmount}}đ đang chờ xác nhận.";
    private static final String MAIL_SUBJECT = "[ShopNow] Đã nhận đơn hàng {{orderCode}}";
    private static final String MAIL_BODY    = "Chào {{fullName}},\nShopNow đã nhận đơn {{orderCode}} ({{totalAmount}}đ).";

    private final NotificationRepo notificationRepo;
    private final IdempotencyService idempotencyService;
    private final UserClient userClient;
    private final NotificationPreferenceGateService notificationPreferenceGateService;
    private final NotificationSender notificationSender;

    public void onOrderCreated(String eventId, OrderCreatedPayload payload)
    {
        if(idempotencyService.isProcessed(eventId))
        {
            log.info("Event {} đã xử lý, bỏ qua", eventId);
            return;
        }

        UUID userId = UUID.fromString(payload.getUserId());

        InternalUserResponse userResponse = userClient.getUser(payload.getUserId());

        Map<String, Object> vars = Map.of(
                "orderCode",   payload.getOrderCode(),
                "totalAmount", String.format("%,d", payload.getTotalAmount()),
                "fullName",    userResponse.getFullName() != null ? userResponse.getFullName() : "bạn"
        );

        List<Notification> toSave = new ArrayList<>();
        toSave.add(build(userId, NotificationChannel.IN_APP, payload, eventId, null,
                TemplateRenderer.render(INAPP_TITLE, vars),
                TemplateRenderer.render(INAPP_BODY, vars)));

        if (notificationPreferenceGateService.allows(userId, NotificationType.ORDER, NotificationChannel.EMAIL)) {
            toSave.add(build(userId, NotificationChannel.EMAIL, payload, eventId, userResponse.getEmail(),
                    TemplateRenderer.render(MAIL_SUBJECT, vars),
                    TemplateRenderer.render(MAIL_BODY, vars)));
        } else {
            log.info("User {} tắt email order updates -> bỏ kênh EMAIL", userId);
        }

        List<Notification> saved = notificationRepo.saveAll(toSave);

        idempotencyService.markProcessed(eventId);

        saved.forEach(notification -> notificationSender.dispatch(notification.getId()));

    }

    private Notification build(UUID userId, NotificationChannel channel, OrderCreatedPayload p,
                               String eventId, String recipient, String title, String body) {
        return Notification.builder()
                .userId(userId)
                .channel(channel)
                .type(NotificationType.ORDER)
                .title(title)
                .body(body)
                .actionUrl("/orders/" + p.getOrderId())
                .recipient(recipient)
                .status(NotificationStatus.PENDING)
                .refEventId(parseUuidOrNull(eventId))
                .build();
    }

    private static UUID parseUuidOrNull(String s) {
        try { return UUID.fromString(s); } catch (Exception e) { return null; }
    }
}
