package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public User findByUsernameOrEmail(String usernameOrEmail) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, usernameOrEmail)
                .or()
                .eq(User::getEmail, usernameOrEmail));
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    @Override
    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User register(String username, String rawPassword, String email) {
        if (existsByUsername(username)) throw new IllegalArgumentException("用户名已存在");
        if (email != null && existsByEmail(email)) throw new IllegalArgumentException("邮箱已被使用");

        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPassword));
        u.setEmail(email);
        u.setRole("USER");
        u.setCreditScore(100);
        u.setStatus("ACTIVE");
        u.setCreatedAt(LocalDateTime.now());
        userMapper.insert(u);
        return u;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(User user) {
        userMapper.updateById(user);
    }
}
