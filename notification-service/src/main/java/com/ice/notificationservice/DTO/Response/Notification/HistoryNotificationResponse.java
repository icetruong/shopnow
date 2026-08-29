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
public class HistoryNotificationResponse {
    private String notificationId;
    private String channel;
    private String type;
    private String recipient;
    private String status;
    private Instant sentAt;
}
