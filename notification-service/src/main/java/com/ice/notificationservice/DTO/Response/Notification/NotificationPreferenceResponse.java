package com.ice.notificationservice.DTO.Response.Notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationPreferenceResponse {
    private EmailPreferenceResponse email;
    private SmsPreferenceResponse sms;
    private PushPreferenceResponse push;
}
