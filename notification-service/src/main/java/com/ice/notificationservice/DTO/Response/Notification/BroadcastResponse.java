package com.ice.notificationservice.DTO.Response.Notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BroadcastResponse {
    private String broadcastId;
    private Integer estimatedReach;
    private String status;
}
