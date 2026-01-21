package org.example.auction.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性 (绑定到 application.properties 中的 jwt.*)
 */
@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    // getters / setters
    /**
     * 用于 HMAC 签名的 secret（至少 32 字节）
     */
    private String secret = "56035A954E6AB0FD5E69B7E89C996A22B12D843AC9C6E5245C34461B7C40C2F6";

    /**
     * 过期时间（秒）
     */
    private long expirationSeconds = 3600L;

    /**
     * HTTP 头名称（默认 Authorization）
     */
    private String header = "Authorization";

    /**
     * token 前缀（例如 Bearer）
     */
    private String tokenPrefix = "Bearer ";

}