package org.example.auction.security;

import org.example.auction.entity.RefreshToken;
import org.example.auction.mapper.RefreshTokenMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 基于数据库的 refresh token 服务：创建 / 旋转 / 撤销
 */
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenMapper mapper;
    private final JwtProperties jwtProperties;

    public RefreshTokenServiceImpl(RefreshTokenMapper mapper, JwtProperties jwtProperties) {
        this.mapper = mapper;
        this.jwtProperties = jwtProperties;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        RefreshToken rt = new RefreshToken();
        rt.setUserId(userId);
        rt.setToken(generateToken());
        rt.setCreatedAt(LocalDateTime.now());
        rt.setRevoked(false);
        rt.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshExpirationDays()));
        mapper.insert(rt);
        return rt;
    }

    @Override
    @Transactional
    public RefreshToken rotateRefreshToken(String token) {
        RefreshToken existing = findByToken(token);
        if (existing == null) throw new IllegalArgumentException("invalid refresh token");
        if (existing.getRevoked() != null && existing.getRevoked()) throw new IllegalArgumentException("refresh token revoked");
        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) throw new IllegalArgumentException("refresh token expired");

        // 撤销旧 token
        existing.setRevoked(true);
        mapper.updateById(existing);

        // 创建新的 refresh token
        return createRefreshToken(existing.getUserId());
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String token) {
        RefreshToken existing = findByToken(token);
        if (existing != null) {
            existing.setRevoked(true);
            mapper.updateById(existing);
        }
    }

    @Override
    public RefreshToken findByToken(String token) {
        if (token == null) return null;
        return mapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getToken, token));
    }

    private String generateToken() {
        // 生成长度较长的随机字符串（UUID + random）
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
