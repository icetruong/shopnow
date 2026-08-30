package com.ice.notificationservice.Service;

import com.ice.notificationservice.Entity.NotificationPreference;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Repository.NotificationPreferenceRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceGateService {
    private final NotificationPreferenceRepo notificationPreferenceRepo;

    public boolean allows(UUID userId, NotificationType type, NotificationChannel channel)
    {
        if (channel == NotificationChannel.IN_APP)
            return true;

        NotificationPreference notificationPreference = notificationPreferenceRepo.findByUserId(UUID.fromString(userId))
                .orElse(null);

        if(notificationPreference == null)
            return channel != NotificationChannel.SMS;

        return switch (channel) {
            case EMAIL -> switch (type) {
                case ORDER, SHIPMENT -> notificationPreference.getEmailOrderUpdates();
                case PAYMENT         -> notificationPreference.getEmailPaymentReceipt();
                case PROMOTION       -> notificationPreference.getEmailPromotions();
                case SYSTEM          -> true;
            };
            case SMS -> switch (type) {
                case SHIPMENT       -> notificationPreference.getSmsDeliveryAlert();
                case ORDER, PAYMENT -> notificationPreference.getSmsOrderUpdates();
                default             -> false;
            };
            case PUSH -> switch (type) {
                case ORDER, PAYMENT, SHIPMENT -> notificationPreference.getPushOrderUpdates();
                case PROMOTION               -> notificationPreference.getPushPromotions();
                case SYSTEM                  -> true;
            };
            default -> throw new IllegalStateException("Unexpected value: " + channel);
        };
    }
}
