package com.ice.notificationservice.Controller;

import com.ice.notificationservice.DTO.Request.Notification.NotificationPreferenceRequest;
import com.ice.notificationservice.DTO.Request.Notification.RegisterDeviceRequest;
import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationPageResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationPreferenceResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationUnreadCountResponse;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> getNotification(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(required = false) NotificationType type,
            Authentication authentication
    ) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy danh sách thông báo thành công",
                        notificationService.getNotification(page, size, isRead, type, userId)
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> getUnreadCount(Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy số thông báo chưa đọc thành công",
                        notificationService.getUnreadCountNotification(userId)
                )
        );
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markReadNotification(@PathVariable String notificationId, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.markRead(notificationId, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã đánh dấu đã đọc.",
                        null
                )
        );
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markReadAllNotification(Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.markReadAll(userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã đánh dấu tất cả đã đọc.",
                        null
                )
        );
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable String notificationId,Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.deleteNotification(notificationId, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã xóa thông báo.",
                        null
                )
        );
    }

    @PostMapping("/devices")
    public ResponseEntity<ApiResponse<Void>> registerDevice(@Valid @RequestBody RegisterDeviceRequest request, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.registerDevice(request, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã đăng ký thiết bị nhận thông báo.",
                        null
                )
        );
    }

    @DeleteMapping("/devices/{deviceToken}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable String deviceToken, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.deleteDevice(deviceToken, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã hủy đăng ký thiết bị.",
                        null
                )
        );
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<NotificationPreferenceResponse>> getNotificationPreference(Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy cài đặt nhận thông báo thành công",
                        notificationService.getPreferenceNotification(userId)
                )
        );
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<Void>> putNotificationPreference(@Valid @RequestBody NotificationPreferenceRequest request, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userId = jwt.getClaimAsString("userId");

        notificationService.putPreferenceNotification(request, userId);

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đã cập nhật cài đặt thông báo.",
                        null
                )
        );
    }
}
