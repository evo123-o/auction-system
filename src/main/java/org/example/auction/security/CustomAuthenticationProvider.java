package org.example.auction.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
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
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public CustomAuthenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        String password = credentials == null ? "" : credentials.toString();

        UserDetails user = userDetailsService.loadUserByUsername(username);
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        // 认证成功：返回包含 authorities 的 Authentication
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
