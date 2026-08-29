package com.ice.notificationservice.DTO.Response.Notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {
    private String notificationId;
    private String type;
    private String title;
    private String body;
    private String imageUrl;
    private String actionUrl;
    private Boolean isRead;
    private Instant createdAt;
}
