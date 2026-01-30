package org.example.auction.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.jspecify.annotations.NullMarked;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Primary
@Service("customUserDetailsService")
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "ROLE_USER";
        return role.startsWith("ROLE_") ? role : "ROLE_" + role;
    }

    @NullMarked
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getUsername, username).last("LIMIT 1");
        User u = userMapper.selectOne(qw);
        if (u == null) throw new UsernameNotFoundException("用户未找到: " + username);

        GrantedAuthority authority = new SimpleGrantedAuthority(normalizeRole(u.getRole()));
        return org.springframework.security.core.userdetails.User.builder()
                .username(u.getUsername())
                .password(u.getPassword())
                .authorities(Collections.singleton(authority))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!"ACTIVE".equalsIgnoreCase(u.getStatus()))
                .build();
    }
}