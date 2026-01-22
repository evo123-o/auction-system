package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl; // 需要导入
import org.example.auction.dto.RegisterRequest;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 简单的 UserService 实现（MyBatis-Plus）
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User findByUsername(String username) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getUsername, username).last("LIMIT 1");
        return userMapper.selectOne(qw);
    }

    @Override
    @Transactional
    public User register(RegisterRequest req) {
        User u = User.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword()))
                .email(req.getEmail())
                .role("USER")
                .creditScore(100)
                .status("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        this.save(u);
        return u;
    }

    @Override
    public User findById(Long id) {
        return this.getById(id);
    }
}
