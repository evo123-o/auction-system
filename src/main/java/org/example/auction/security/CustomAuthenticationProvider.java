package org.example.auction.security;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 简单的 AuthenticationProvider：直接使用 UserDetailsService + PasswordEncoder 验证用户名/密码。
 * 目的：避免对 DaoAuthenticationProvider.setUserDetailsService 的依赖（兼容性更好）。
 */
public record CustomAuthenticationProvider(UserDetailsService userDetailsService,
                                           PasswordEncoder passwordEncoder) implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();

        UserDetails user = userDetailsService.loadUserByUsername(username);
        if (!user.isEnabled()) {
            throw new DisabledException("账号已封禁，无法登录");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 认证成功：返回包含 authorities 的 Authentication
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @NullMarked
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
