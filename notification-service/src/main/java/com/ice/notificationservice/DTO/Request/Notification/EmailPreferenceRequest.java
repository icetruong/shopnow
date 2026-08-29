package com.ice.notificationservice.DTO.Request.Notification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmailPreferenceRequest {
    private Boolean orderUpdates;
    private Boolean promotions;
    private Boolean paymentReceipt;
}
