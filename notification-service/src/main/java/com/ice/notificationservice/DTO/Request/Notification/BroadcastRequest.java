package com.ice.notificationservice.DTO.Request.Notification;

import com.ice.notificationservice.Enum.BroadcastTarget;
import com.ice.notificationservice.Enum.NotificationChannel;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BroadcastRequest {
    @NotNull(message = "channel must not be null")
    private NotificationChannel channel;

    @NotNull(message = "target must not be null")
    private BroadcastTarget target;
    @NotBlank(message = "title must not be blank")
    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    @NotBlank(message = "body must not be blank")
    private String body;

    @Size(max = 2048, message = "imageUrl is too long")
    private String imageUrl;

    @Size(max = 500, message = "actionUrl must be at most 500 characters")
    private String actionUrl;

    /** Optional. null = gửi ngay; có giá trị thì phải ở tương lai. */
    @FutureOrPresent(message = "scheduleAt must be in the future")
    private Instant scheduleAt;
}
