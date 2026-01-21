package org.example.auction.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单内存黑名单：token -> expiryMillis
 * 开发/单实例环境可用；生产请改为 Redis 或集中存储。
 */
@Service
public class TokenBlacklistService {

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token, long expiryMillis) {
        if (token == null) return;
        blacklist.put(token, expiryMillis);
    }

    public boolean isBlacklisted(String token) {
        if (token == null) return false;
        Long exp = blacklist.get(token);
        if (exp == null) return false;
        if (System.currentTimeMillis() > exp) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }
}
