package com.ice.notificationservice.Controller;

import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationPageResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationUnreadCountResponse;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationService;
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
}
