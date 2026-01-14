package org.example.auction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/**
 * 适用于 Spring Boot 4.x / Spring Security 6 的安全配置（无需继承 WebSecurityConfigurerAdapter）。
 * - 允许 /api/auth/** 无需认证（便于注册与测试）
 * - 其余接口需要认证，开发阶段使用 httpBasic；后续建议切换为 JWT
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 开发阶段使用内存用户，生产请替换为从数据库加载的 UserDetailsService。
     */
    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        UserDetails admin = User.withUsername("admin")
                .password(encoder.encode("admin"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    /**
     * SecurityFilterChain 替代旧的 configure(HttpSecurity) 方法。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        // 允许匿名访问的路径（可以列多个）
                        .requestMatchers("/", "/index.html", "/api/auth/**", "/actuator/**", "/error").permitAll()
                        // 其余都需要认证
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults()); // 开发阶段方便使用 basic auth
        return http.build();
    }

    /**
     * 暴露 AuthenticationManager 以便在自定义认证流程中注入使用。
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}