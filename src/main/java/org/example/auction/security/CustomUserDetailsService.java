package org.example.auction.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 从数据库加载用户信息并返回 Spring Security 所需的 UserDetails
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    public CustomUserDetailsService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getUsername, username).last("LIMIT 1");
        User u = userMapper.selectOne(qw);
        if (u == null) {
            throw new UsernameNotFoundException("用户未找到: " + username);
        }

        GrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + (u.getRole() == null ? "USER" : u.getRole()));
        return org.springframework.security.core.userdetails.User.builder()
                .username(u.getUsername())
                .password(u.getPassword()) // 已经是 BCrypt 哈希
                .authorities(Collections.singleton(authority))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!"ACTIVE".equalsIgnoreCase(u.getStatus()))
                .build();
    }
}
