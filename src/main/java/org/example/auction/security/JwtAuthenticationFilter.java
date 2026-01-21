package org.example.auction.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 从 Authorization 头解析 JWT、验证并设置 Authentication
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenUtil jwtTokenUtil;
    private final UserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService blacklistService;

    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil,
                                   UserDetailsService userDetailsService,
                                   JwtProperties jwtProperties,
                                   TokenBlacklistService blacklistService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userDetailsService = userDetailsService;
        this.jwtProperties = jwtProperties;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(jwtProperties.getHeader());
        if (StringUtils.hasText(header) && header.startsWith(jwtProperties.getTokenPrefix())) {
            String token = header.substring(jwtProperties.getTokenPrefix().length());
            // 黑名单检查
            if (!blacklistService.isBlacklisted(token) && jwtTokenUtil.validateToken(token)) {
                String username = jwtTokenUtil.getUsernameFromToken(token);
                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    if (userDetails != null) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
