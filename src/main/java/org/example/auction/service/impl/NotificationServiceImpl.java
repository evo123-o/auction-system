package org.example.auction.service.impl;

import org.example.auction.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationServiceImpl implements NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Override
    public void notifyUser(Long userId, String message) {
        // 简单日志，生产中改为邮件/短信/站内信
        log.info("Notify user {}: {}", userId, message);
    }
}
