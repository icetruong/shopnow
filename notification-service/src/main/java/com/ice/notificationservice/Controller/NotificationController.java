package com.ice.notificationservice.Controller;

import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.DTO.Response.Notification.NotificationPageResponse;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
