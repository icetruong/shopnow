package com.ice.notificationservice.Service;

import com.ice.notificationservice.DTO.Request.Notification.EmailPreferenceRequest;
import com.ice.notificationservice.DTO.Request.Notification.NotificationPreferenceRequest;
import com.ice.notificationservice.DTO.Request.Notification.PushPreferenceRequest;
import com.ice.notificationservice.DTO.Request.Notification.RegisterDeviceRequest;
import com.ice.notificationservice.DTO.Request.Notification.SmsPreferenceRequest;
import com.ice.notificationservice.DTO.Response.Notification.*;
import com.ice.notificationservice.Entity.DeviceToken;
import com.ice.notificationservice.Entity.Notification;
import com.ice.notificationservice.Entity.NotificationPreference;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Exception.NotificationNotFoundException;
import com.ice.notificationservice.Repository.DeviceTokenRepo;
import com.ice.notificationservice.Repository.NotificationPreferenceRepo;
import com.ice.notificationservice.Repository.NotificationRepo;
import com.ice.notificationservice.Util.NotificationSpecification;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final NotificationRepo notificationRepo;
    private final DeviceTokenRepo deviceTokenRepo;
    private final NotificationPreferenceRepo notificationPreferenceRepo;

    public NotificationPageResponse getNotification(int page, int size, Boolean isRead, NotificationType type, String userId) {

        UUID currentUserId = UUID.fromString(userId);
        int safePage = Math.max(page, 0);
        int safeSize = normalizeSize(size);

        Specification<Notification> specification = Specification
                .where(NotificationSpecification.hasUserId(currentUserId))
                .and(NotificationSpecification.hasIsRead(isRead))
                .and(NotificationSpecification.hasNotificationType(type));

        PageRequest pageRequest = PageRequest.of(
                safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Notification> notifications = notificationRepo.findAll(specification, pageRequest);

        long unread = notificationRepo.countByUserIdAndIsRead(currentUserId, false);

        return new NotificationPageResponse(
                notifications.getContent().stream()
                        .map(notification -> new NotificationResponse(
                                notification.getId().toString(),
                                notification.getType().name(),
                                notification.getTitle(),
                                notification.getBody(),
                                notification.getImageUrl(),
                                notification.getActionUrl(),
                                notification.getIsRead(),
                                notification.getCreatedAt().toInstant(ZoneOffset.UTC)
                        )).toList(),
                notifications.getNumber(),
                notifications.getSize(),
                notifications.getTotalElements(),
                notifications.getTotalPages(),
                unread
        );
    }

    @Cacheable(value = "noti:unread", key = "#userId")
    public NotificationUnreadCountResponse getUnreadCountNotification(String userId) {

        long unread = notificationRepo.countByUserIdAndIsRead(UUID.fromString(userId), false);

        return new NotificationUnreadCountResponse(unread);
    }

    @CacheEvict(value = "noti:unread", key = "#userId")
    public void markRead(String notificationId, String userId) {
        Notification notification = notificationRepo.findByIdAndUserId(UUID.fromString(notificationId), UUID.fromString(userId))
                .orElseThrow(() -> new NotificationNotFoundException("Notification not found: " + notificationId));

        notification.setIsRead(true);
        notificationRepo.save(notification);
    }

    @Transactional
    @CacheEvict(value = "noti:unread", key = "#userId")
    public void markReadAll(String userId) {
        notificationRepo.markAllReadByUserId(UUID.fromString(userId));
    }

    @Transactional
    @CacheEvict(value = "noti:unread", key = "#userId")
    public void deleteNotification(String notificationId, String userId) {
        long deleted = notificationRepo.deleteByIdAndUserId(UUID.fromString(notificationId), UUID.fromString(userId));
        if (deleted == 0) {
            throw new NotificationNotFoundException("Notification not found: " + notificationId);
        }
    }

    @Transactional
    public void registerDevice(RegisterDeviceRequest request, String userId) {
        // Upsert theo device_token (UNIQUE): gọi lại với cùng token -> update, không insert trùng.
        // Cũng xử lý token đổi chủ khi user khác đăng nhập trên cùng thiết bị.
        DeviceToken deviceToken = deviceTokenRepo.findByDeviceToken(request.getDeviceToken())
                .orElseGet(DeviceToken::new);

        deviceToken.setUserId(UUID.fromString(userId));
        deviceToken.setDeviceToken(request.getDeviceToken());
        deviceToken.setPlatform(request.getPlatform());
        deviceToken.setDeviceName(request.getDeviceName());
        deviceToken.setIsActive(true);
        deviceToken.setLastUsedAt(LocalDateTime.now(ZoneOffset.UTC));

        deviceTokenRepo.save(deviceToken);
    }

    @Transactional
    public void deleteDevice(String deviceToken, String userId) {
        // Idempotent: token không tồn tại / không thuộc user thì bỏ qua, logout vẫn OK.
        deviceTokenRepo.deleteByDeviceTokenAndUserId(deviceToken, UUID.fromString(userId));
    }

    public NotificationPreferenceResponse getPreferenceNotification(String userId) {
        NotificationPreference notificationPreference = notificationPreferenceRepo.findByUserId(UUID.fromString(userId))
                .orElseGet(NotificationPreference::new);

        notificationPreference.setUserId(UUID.fromString(userId));
        notificationPreferenceRepo.save(notificationPreference);

        return new NotificationPreferenceResponse(
                new EmailPreferenceResponse(
                        notificationPreference.getEmailOrderUpdates(),
                        notificationPreference.getEmailPromotions(),
                        notificationPreference.getEmailPaymentReceipt()
                ),
                new SmsPreferenceResponse(
                        notificationPreference.getSmsOrderUpdates(),
                        notificationPreference.getSmsDeliveryAlert()
                ),
                new PushPreferenceResponse(
                        notificationPreference.getPushOrderUpdates(),
                        notificationPreference.getPushPromotions(),
                        notificationPreference.getPushFlashSale()
                )
        );
    }

    @Transactional
    public void putPreferenceNotification(NotificationPreferenceRequest request, String userId) {
        NotificationPreference pref = notificationPreferenceRepo.findByUserId(UUID.fromString(userId))
                .orElseGet(NotificationPreference::new);

        pref.setUserId(UUID.fromString(userId));

        // Partial update: field nào client không gửi (null) thì giữ nguyên giá trị cũ.
        EmailPreferenceRequest email = request.getEmail();
        if (email != null) {
            if (email.getOrderUpdates() != null)   pref.setEmailOrderUpdates(email.getOrderUpdates());
            if (email.getPromotions() != null)     pref.setEmailPromotions(email.getPromotions());
            if (email.getPaymentReceipt() != null) pref.setEmailPaymentReceipt(email.getPaymentReceipt());
        }

        SmsPreferenceRequest sms = request.getSms();
        if (sms != null) {
            if (sms.getOrderUpdates() != null)  pref.setSmsOrderUpdates(sms.getOrderUpdates());
            if (sms.getDeliveryAlert() != null) pref.setSmsDeliveryAlert(sms.getDeliveryAlert());
        }

        PushPreferenceRequest push = request.getPush();
        if (push != null) {
            if (push.getOrderUpdates() != null) pref.setPushOrderUpdates(push.getOrderUpdates());
            if (push.getPromotions() != null)   pref.setPushPromotions(push.getPromotions());
            if (push.getFlashSale() != null)    pref.setPushFlashSale(push.getFlashSale());
        }

        notificationPreferenceRepo.save(pref);
    }

    private int normalizeSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
