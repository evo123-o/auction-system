package org.example.auction.config;

import org.example.auction.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

/**
 * Security 配置：显式创建 DaoAuthenticationProvider + ProviderManager（AuthenticationManager），
 * 并把 JwtAuthenticationFilter 加入过滤链（在 UsernamePasswordAuthenticationFilter 之前）。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public SecurityConfig(UserDetailsService userDetailsService,
                          JwtTokenUtil jwtTokenUtil,
                          JwtProperties jwtProperties,
                          TokenBlacklistService tokenBlacklistService) {
        this.userDetailsService = userDetailsService;
        this.jwtTokenUtil = jwtTokenUtil;
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        CustomAuthenticationProvider provider = new CustomAuthenticationProvider(uds, encoder);
        return new ProviderManager(List.of(provider));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenUtil, userDetailsService, jwtProperties, tokenBlacklistService);
        JwtAuthenticationEntryPoint entryPoint = new JwtAuthenticationEntryPoint();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/index.html", "/api/auth/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                // 不使用 session（根据需要可以改）
                .sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
                .httpBasic(Customizer.withDefaults());

        // 在 UsernamePasswordAuthenticationFilter 之前放入 JWT 过滤器
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}