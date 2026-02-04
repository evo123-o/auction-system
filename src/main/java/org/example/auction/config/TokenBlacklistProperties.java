package org.example.auction.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@EnableConfigurationProperties(TokenBlacklistProperties.class)
@ConfigurationProperties(prefix = "token.blacklist")
public class TokenBlacklistProperties {

    /**
     * 黑名单存储类型， redis
     */
    private String store;

}
