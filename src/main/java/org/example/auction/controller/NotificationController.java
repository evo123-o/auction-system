package org.example.auction.controller;

import java.io.IOException;
import java.util.Map;

import org.example.auction.dto.ApiResponse;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUserService currentUserService;

    public NotificationController(NotificationService notificationService, CurrentUserService currentUserService) {
        this.notificationService = notificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping(path = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalArgumentException("未授权");
        }

        Long userId = currentUserService.getCurrentUserId().orElse(null);
        if (userId == null) {
            throw new IllegalArgumentException("未找到用户");
        }

        SseEmitter emitter = new SseEmitter(3600000L); // 1小时超时

        // 注册到 Service 中，这里需要我们在 Service 中添加 registerEmitter 方法
        notificationService.registerEmitter(userId, emitter);

        // 发送连接成功消息
        try {
            emitter.send(SseEmitter.event().name("connected").data("订阅成功"));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        
        return emitter;
    }

            @GetMapping("/messages")
            public ResponseEntity<?> messages() {
                Long userId = currentUserService.getCurrentUserId().orElse(null);
                if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
                return ResponseEntity.ok(ApiResponse.ok(notificationService.listMessages(userId)));
            }

            @GetMapping("/announcements")
            public ResponseEntity<?> announcements() {
                Long userId = currentUserService.getCurrentUserId().orElse(null);
                if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
                return ResponseEntity.ok(ApiResponse.ok(notificationService.listAnnouncements(userId)));
            }

            @GetMapping("/unread-count")
            public ResponseEntity<?> unreadCount() {
                Long userId = currentUserService.getCurrentUserId().orElse(null);
                if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
                return ResponseEntity.ok(ApiResponse.ok(Map.of("count", notificationService.countUnread(userId))));
            }

            @PutMapping("/{id}/read")
            public ResponseEntity<?> markAsRead(@PathVariable("id") Long id) {
                Long userId = currentUserService.getCurrentUserId().orElse(null);
                if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
                boolean success = notificationService.markAsRead(userId, id);
                if (!success) return ResponseEntity.status(404).body(ApiResponse.fail("消息不存在"));
                return ResponseEntity.ok(ApiResponse.ok("ok"));
            }

            @PutMapping("/read-all")
            public ResponseEntity<?> markAllAsRead(@RequestParam(name = "scope", required = false, defaultValue = "all") String scope) {
                Long userId = currentUserService.getCurrentUserId().orElse(null);
                if (userId == null) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
                int affected = notificationService.markAllAsRead(userId, scope);
                return ResponseEntity.ok(ApiResponse.ok(Map.of("updated", affected)));
            }

            @PostMapping("/announcements/publish-all")
            @PreAuthorize("hasRole('ADMIN')")
            public ResponseEntity<?> publishAnnouncementToAll(@Validated @RequestBody PublishAnnouncementRequest request) {
                int affected = notificationService.publishAnnouncementToAll(request.title(), request.content());
                return ResponseEntity.ok(ApiResponse.ok(Map.of("published", affected)));
            }

            public record PublishAnnouncementRequest(
                    @NotBlank(message = "标题不能为空") String title,
                    @NotBlank(message = "内容不能为空") String content) {
            }
}