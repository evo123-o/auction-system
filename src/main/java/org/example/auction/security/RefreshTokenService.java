package org.example.auction.security;

import org.example.auction.entity.RefreshToken;

public interface RefreshTokenService {
    /**
     * 创建并持久化一个 refresh token
     */
    RefreshToken createRefreshToken(Long userId);

    /**
     * 根据 refresh token 字符串校验并进行旋转（使旧 token 失效并返回新的 refresh token）
     * 返回新的 RefreshToken（并可用于生成新的 access token）
     */
    RefreshToken rotateRefreshToken(String token);

    /**
     * 撤销 refresh token（如 logout）
     */
    void revokeRefreshToken(String token);

    /**
     * 查找 refresh token
     */
    RefreshToken findByToken(String token);
}
