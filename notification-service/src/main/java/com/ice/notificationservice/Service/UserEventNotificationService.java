package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Event.Consumer.UserPasswordResetPayload;
import com.ice.notificationservice.DTO.Event.Consumer.UserRegisteredPayload;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationDraft.Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class UserEventNotificationService {

    private static final Template WELCOME_EMAIL = new Template(NotificationChannel.EMAIL,
            "Chào mừng bạn đến với ShopNow!",
            "Chào {{fullName}},\nCảm ơn bạn đã tạo tài khoản ShopNow. Chúc bạn mua sắm vui vẻ!");

    private static final Template RESET_PASSWORD_EMAIL = new Template(NotificationChannel.EMAIL,
            "[ShopNow] Yêu cầu đặt lại mật khẩu",
            "Bạn vừa yêu cầu đặt lại mật khẩu.\n"
                    + "Nhấn liên kết sau để đặt lại (hết hạn lúc {{expiresAt}}):\n{{resetLink}}\n"
                    + "Nếu không phải bạn, hãy bỏ qua email này.");

    private final NotificationPipeline pipeline;
    private final String resetPasswordUrl;

    public UserEventNotificationService(
            NotificationPipeline pipeline,
            @Value("${app.frontend.reset-password-url}") String resetPasswordUrl) {
        this.pipeline = pipeline;
        this.resetPasswordUrl = resetPasswordUrl;
    }

    /** user.registered — email nằm sẵn trong payload, không gọi service nào. */
    public void onUserRegistered(String eventId, UserRegisteredPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        Map<String, Object> vars = Map.of(
                "fullName", p.getFullName() != null ? p.getFullName() : "bạn");

        pipeline.deliver(eventId, NotificationType.SYSTEM, UUID.fromString(p.getUserId()), null,
                List.of(WELCOME_EMAIL.render(p.getEmail(), vars)));
    }

    /**
     * user.password_reset_requested — email + token nằm sẵn trong payload.
     * Mail giao dịch -> deliverAlways (không lọc preference).
     */
    public void onPasswordResetRequested(String eventId, UserPasswordResetPayload p) {
        if (pipeline.alreadyProcessed(eventId)) return;

        Map<String, Object> vars = Map.of(
                "resetLink", resetPasswordUrl + "?token=" + p.getResetToken(),
                "expiresAt", p.getExpiresAt() != null ? p.getExpiresAt() : "");

        pipeline.deliverAlways(eventId, NotificationType.SYSTEM, UUID.fromString(p.getUserId()), null,
                List.of(RESET_PASSWORD_EMAIL.render(p.getEmail(), vars)));
    }
}
