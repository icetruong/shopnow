package com.ice.notificationservice.DTO.Request.Notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferenceRequest {
    private EmailPreferenceRequest email;
    private SmsPreferenceRequest sms;
    private PushPreferenceRequest push;
}
