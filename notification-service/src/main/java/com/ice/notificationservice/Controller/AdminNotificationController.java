package com.ice.notificationservice.Controller;

import com.ice.notificationservice.DTO.Request.Notification.BroadcastRequest;
import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.DTO.Response.Notification.BroadcastResponse;
import com.ice.notificationservice.DTO.Response.Notification.HistoryNotificationPageResponse;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<HistoryNotificationPageResponse>> getNotificationHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate
    )
    {
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy lịch sử gửi thông báo thành công",
                        notificationService.getHistoryNotification(page, size, channel, status, startDate)
                )
        );
    }

    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<BroadcastResponse>> broadcast(@Valid @RequestBody BroadcastRequest request, Authentication authentication)
    {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String adminId = jwt.getClaimAsString("userId");

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Gửi thông báo hàng loạt thành công.",
                        notificationService.broadcastNotification(request, adminId)
                )
        );
    }
}
