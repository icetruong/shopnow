package com.ice.notificationservice.Controller;

import com.ice.notificationservice.DTO.Response.Common.ApiResponse;
import com.ice.notificationservice.DTO.Response.Notification.HistoryNotificationPageResponse;
import com.ice.notificationservice.Enum.NotificationChannel;
import com.ice.notificationservice.Enum.NotificationStatus;
import com.ice.notificationservice.Enum.NotificationType;
import com.ice.notificationservice.Service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
