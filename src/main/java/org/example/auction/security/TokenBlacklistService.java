package org.example.auction.security;

/**
 * 黑名单接口：用于处理 access token 黑名单（登出/撤销 token）
 */
public interface TokenBlacklistService {
    /**
     * 将 token 加入黑名单，expiryMillis 为 token 的到期时间（毫秒）
     */
    void blacklist(String token, long expiryMillis);

    /**
     * token 是否在黑名单中（若已过期会自动清理返回 false）
     */
    boolean isBlacklisted(String token);
}
