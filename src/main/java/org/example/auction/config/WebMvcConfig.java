package org.example.auction.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将本地上传目录映射为静态资源路径，便于通过 URL 访问上传的图片。
 * 请在 application.properties 中配置：
 * app.upload.dir=uploads
 * app.upload.base-url=/uploads
 * 然后通过 <a href="http://localhost:8080/uploads/">...</a>{filename} 访问文件。
 * 同时配置 CORS 以允许前端跨域访问。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /uploads/** 映射到本地文件系统目录（注意 file: 前缀）
        String location = "file:" + uploadDir + "/";
        String pattern = uploadBaseUrl + "/**";
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS configuration for development (do not use wildcard origins in production)
        // TODO: Replace allowedOriginPatterns("*") with trusted frontend domain(s) before production deployment
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
