package org.example.auction.service;

import java.util.List;

import org.example.auction.entity.Notification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {
    void notifyUser(Long userId, String message);

    void notifyUser(Long userId, String type, String title, String content);

    void registerEmitter(Long userId, SseEmitter emitter);

    List<Notification> listMessages(Long userId);

    List<Notification> listAnnouncements(Long userId);

    long countUnread(Long userId);

    boolean markAsRead(Long userId, Long notificationId);

    int markAllAsRead(Long userId, String scope);

    int publishAnnouncementToAll(String title, String content);
}
