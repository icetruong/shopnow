package com.ice.notificationservice.Service;

import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Util.TemplateRenderer;

import java.util.Map;

/**
 * Mảnh dùng chung cho mọi consumer:
 *  - {@link Template}: khuôn 1 kênh (title + body có {{var}}), khai báo static final trong service.
 *  - {@link Message}: kết quả đã render, sẵn sàng để {@code NotificationPipeline} INSERT.
 */
public final class NotificationDraft {

    private NotificationDraft() {}

    public record Template(NotificationChannel channel, String title, String body) {

        public Message render(String recipient, Map<String, Object> vars) {
            return new Message(
                    channel,
                    recipient,
                    title == null ? null : TemplateRenderer.render(title, vars),
                    body == null ? null : TemplateRenderer.render(body, vars));
        }
    }

    public record Message(NotificationChannel channel, String recipient, String title, String body) {}
}
