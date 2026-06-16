package com.marketly.notification.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.notification.entity.Notification;
import com.marketly.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notification management")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List my notifications (paginated)")
    public ResponseEntity<ApiResponse<Page<Notification>>> listNotifications(
            @RequestHeader("X-User-ID") String userId,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Notification> result = notificationService
            .listForUser(UUID.fromString(userId), page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread in-app notifications")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(
            @RequestHeader("X-User-ID") String userId) {
        long count = notificationService.countUnread(UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(Map.of("unreadCount", count)));
    }

    @PostMapping("/mark-all-read")
    @Operation(summary = "Mark all in-app notifications as read")
    public ResponseEntity<Void> markAllRead(
            @RequestHeader("X-User-ID") String userId) {
        notificationService.markAllRead(UUID.fromString(userId));
        return ResponseEntity.noContent().build();
    }
}
