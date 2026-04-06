package org.example.auction.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.example.auction.entity.Notification;
import org.example.auction.entity.User;
import org.example.auction.mapper.NotificationMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private UserMapper userMapper;

    // SSE 存储 Map
    private final Map<Long, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    @Override
    public void registerEmitter(Long userId, SseEmitter emitter) {
        userEmitters.put(userId, emitter);
        
        // 注册回调：完成或超时或错误时从 Map 移除
        emitter.onCompletion(() -> userEmitters.remove(userId));
        emitter.onTimeout(() -> userEmitters.remove(userId));
        emitter.onError(e -> userEmitters.remove(userId));
    }

    @Override
    public void notifyUser(Long userId, String message) {
        // 重载默认调用，视为系统通知
        notifyUser(userId, "SYSTEM", "系统通知", message);
    }

    @Override
    public void notifyUser(Long userId, String type, String title, String content) {
        log.info("Notify user [{}] {}: {}", userId, type, title);
        
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        
        notificationMapper.insert(notification);
        
        // 尝试发送 SSE 实时推送
        SseEmitter emitter = userEmitters.get(userId);
        if (emitter != null) {
            try {
                // 发送刚刚落库的消息
                emitter.send(SseEmitter.event().data(notification));
            } catch (IOException e) {
                // 如果发送失败表明通道已断开，直接移除不影响逻辑
                userEmitters.remove(userId);
                log.warn("Failed to push SSE to user {}", userId, e);
            }
        }
    }

    @Override
    public List<Notification> listMessages(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .ne(Notification::getType, "SYSTEM")
                .orderByDesc(Notification::getCreatedAt));
    }

    @Override
    public List<Notification> listAnnouncements(Long userId) {
        return notificationMapper.selectList(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getType, "SYSTEM")
                .orderByDesc(Notification::getCreatedAt));
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false));
    }

    @Override
    public boolean markAsRead(Long userId, Long notificationId) {
        int rows = notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .eq(Notification::getUserId, userId)
                .set(Notification::getIsRead, true));
        return rows > 0;
    }

    @Override
    public int markAllAsRead(Long userId, String scope) {
        LambdaUpdateWrapper<Notification> wrapper = new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, false);

        if ("messages".equalsIgnoreCase(scope)) {
            wrapper.ne(Notification::getType, "SYSTEM");
        } else if ("announcements".equalsIgnoreCase(scope)) {
            wrapper.eq(Notification::getType, "SYSTEM");
        }

        wrapper.set(Notification::getIsRead, true);
        return notificationMapper.update(null, wrapper);
    }

    @Override
    public int publishAnnouncementToAll(String title, String content) {
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .isNotNull(User::getId));

        if (users == null || users.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (User user : users) {
            if (user.getId() == null) {
                continue;
            }
            notifyUser(user.getId(), "SYSTEM", title, content);
            count++;
        }
        return count;
    }
}
