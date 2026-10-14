package com.apexinstitute.controller;

import com.apexinstitute.dto.NotificationListDto;
import com.apexinstitute.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public NotificationListDto list() {
        return notifications.list();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        notifications.markRead(id);
        return ResponseEntity.ok(Map.of("message", "Marked as read"));
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead() {
        int count = notifications.markAllRead();
        return ResponseEntity.ok(Map.of("message", "Marked all as read", "count", count));
    }
}