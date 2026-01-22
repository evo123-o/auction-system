package org.example.auction.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Redis 存储黑名单（适合多实例）。
 * Key 格式：blacklist:token:{token}
 */
@Service
@ConditionalOnProperty(prefix = "token.blacklist", name = "store", havingValue = "redis")
public class RedisTokenBlacklistService implements TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix = "blacklist:token:";

    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String token, long expiryMillis) {
        if (token == null) return;
        long ttlMillis = Math.max(0, expiryMillis - System.currentTimeMillis());
        long ttlSeconds = TimeUnit.MILLISECONDS.toSeconds(ttlMillis);
        if (ttlSeconds <= 0) return;
        String key = keyPrefix + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isBlacklisted(String token) {
        if (token == null) return false;
        String key = keyPrefix + token;
        Boolean exist = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exist);
    }
}
