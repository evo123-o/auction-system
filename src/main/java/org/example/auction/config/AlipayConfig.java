package org.example.auction.config;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 支付宝配置类
 * 配置支付宝沙箱环境参数并创建支付宝客户端
 */
@Configuration
@ConfigurationProperties(prefix = "alipay")
@Data
public class AlipayConfig {

    /**
     * 应用ID - 从支付宝开放平台获取
     */
    private String appId;

    /**
     * 商户私钥 - RSA2私钥，用于生成签名
     */
    private String merchantPrivateKey;

    /**
     * 支付宝公钥 - 用于验证支付宝返回的签名
     */
    private String alipayPublicKey;

    /**
     * 网关地址 - 沙箱环境: https://openapi.alipaydev.com/gateway.do
     */
    private String gatewayUrl;

    /**
     * 签名类型 - RSA2
     */
    private String signType;

    /**
     * 字符编码 - UTF-8
     */
    private String charset;

    /**
     * 异步通知地址 - 支付宝服务器主动通知商户服务器里指定的页面
     */
    private String notifyUrl;

    /**
     * 同步返回地址 - 支付完成后返回的页面
     */
    private String returnUrl;

    /**
     * 创建支付宝客户端实例
     * @return AlipayClient
     */
    @Bean
    public AlipayClient alipayClient() {
        return new DefaultAlipayClient(
                gatewayUrl,
                appId,
                merchantPrivateKey,
                "json",
                charset,
                alipayPublicKey,
                signType
        );
    }
}
