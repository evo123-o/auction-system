package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.constraints.NotNull;
import lombok.NonNull;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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

    // ===== 新增实现：分页查询、保存与删除 =====
    @NotNull
    @Override
    public Page<@NonNull User> findAll(@NonNull Pageable pageable) {
        // 简单实现：查询全部并在内存中分页（适用于用户量不大的测试/管理场景)
        List<User> all = userMapper.selectList(null);
        int total = all.size();
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int from = pageNumber * pageSize;
        int to = Math.min(from + pageSize, total);
        List<User> content = (from >= total || from < 0) ? List.of() : all.subList(from, to);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User saveUser(User user) {
        if (user.getId() == null) {
            if (user.getPassword() != null) {
                user.setPassword(encoder.encode(user.getPassword()));
            }
            user.setCreatedAt(LocalDateTime.now());
            userMapper.insert(user);
            return user;
        } else {
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            return userMapper.selectById(user.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long id) {
        userMapper.deleteById(id);
    }
}
