package org.example.auction.config;

import org.example.auction.security.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;  // 引入PasswordEncoder
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Spring Security 配置类
 * 负责配置应用程序的安全策略，采用JWT无状态认证模式
 * 设计特点：
 * 1. 与AppConfig分离，避免Bean定义冲突
 * 2. 专注于安全相关的配置
 * 3. 支持支付宝回调接口的特殊安全需求
 */
@Configuration
@EnableWebSecurity  // 启用Spring Security Web安全功能
@EnableMethodSecurity  // 启用方法级别安全控制（@PreAuthorize等注解）
public class SecurityConfig {

    private final JwtTokenUtil jwtTokenUtil;      // JWT工具类，用于生成和验证token
    private final JwtProperties jwtProperties;    // JWT配置属性
    private final TokenBlacklistService tokenBlacklistService;  // Token黑名单服务
    private final UserDetailsService userDetailsService;        // 用户详情服务

    /**
     * 构造函数注入依赖
     * @param userDetailsService 自定义用户详情服务实现
     * @param jwtTokenUtil JWT工具类
     * @param jwtProperties JWT配置属性
     * @param tokenBlacklistService Token黑名单服务
     */
    public SecurityConfig(@Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
                          JwtTokenUtil jwtTokenUtil,
                          JwtProperties jwtProperties,
                          TokenBlacklistService tokenBlacklistService) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 认证管理器Bean
     * 通过依赖注入使用AppConfig中定义的PasswordEncoder
     * 设计优势：
     * 1. 避免Bean定义冲突
     * 2. 保持配置的统一性
     * 3. 利用Spring的依赖注入机制
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder encoder) {
        // Spring会自动注入AppConfig中定义的PasswordEncoder实例
        CustomAuthenticationProvider provider = new CustomAuthenticationProvider(userDetailsService, encoder);
        return new ProviderManager(List.of(provider));
    }

    /**
     * 安全过滤器链配置
     * 定义了完整的HTTP安全策略，包括CORS、CSRF、会话管理、授权规则等
          * 关键配置说明：
     * 1. CORS：启用跨域支持
     * 2. CSRF：禁用（JWT无状态模式）
     * 3. Session：无状态会话管理
     * 4. Authorization：细粒度的路径权限控制
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthenticationManager authenticationManager) throws Exception {
        // 创建JWT认证过滤器，负责解析和验证JWT token
        JwtAuthenticationFilter jwtFilter =
                new JwtAuthenticationFilter(jwtTokenUtil, userDetailsService, jwtProperties, tokenBlacklistService);

        // 创建认证入口点，处理未认证用户的访问请求
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

        // 配置HTTP安全规则
        http
                .cors(Customizer.withDefaults())  // 启用CORS跨域支持，默认配置
                .csrf(AbstractHttpConfigurer::disable)  // 禁用CSRF保护（JWT无状态模式必需）
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))  // 设置认证异常处理器
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态会话管理
                .authorizeHttpRequests(auth -> auth  // 配置请求授权规则
                        // 允许匿名访问的路径（无需登录即可访问）
                        .requestMatchers("/", "/index.html", "/error").permitAll()  // 主页、错误页面
                        .requestMatchers("/api/auth/**").permitAll()  // 认证接口（登录、注册等）
                        .requestMatchers("/uploads/**", "/receipts/**", "/static/**").permitAll()  // 静态资源和上传文件
                        .requestMatchers("/favicon.ico").permitAll()  // 网站图标
                        .requestMatchers("/actuator/**").permitAll()  // 监控端点
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()  // API文档
                        .anyRequest().authenticated()  // 其他所有请求都需要认证
                )
                .authenticationManager(authenticationManager);  // 设置认证管理器

        // 将JWT过滤器添加到过滤器链中，在UsernamePasswordAuthenticationFilter之前执行
        // 这样可以在传统表单认证之前先尝试JWT认证
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}