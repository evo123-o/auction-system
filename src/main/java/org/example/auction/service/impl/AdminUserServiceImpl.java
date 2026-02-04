package org.example.auction.service.impl;

import lombok.NonNull;
import org.example.auction.dto.AdminUserDto;
import org.example.auction.dto.CreateUserRequest;
import org.example.auction.dto.UpdateUserRequest;
import org.example.auction.entity.User;
import org.example.auction.service.AdminUserService;
import org.example.auction.service.UserService;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 简单实现，依赖项目已有的 UserService。需要 UserService 提供基本的方法：
 * findById, findByUsername, findAll(Pageable), existsByUsername, saveUser, deleteById
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Page<@NonNull AdminUserDto> listUsers(int page, int size) {
        Pageable p = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<@NonNull User> users = userService.findAll(p);
        return users.map(AdminUserDto::fromEntity);
    }

    @Override
    public AdminUserDto getUser(Long id) {
        User u = userService.findById(id);
        return AdminUserDto.fromEntity(u);
    }

    @Override
    public AdminUserDto createUser(CreateUserRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (userService.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setEmail(req.getEmail());
        // 使用单一 role 字段（项目中 User 使用 String role）
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            // 取第一个角色作为主角色
            String first = req.getRoles().iterator().next();
            u.setRole(first);
        } else {
            u.setRole("USER");
        }
        // enabled -> status
        u.setStatus("ACTIVE");
        User saved = userService.saveUser(u);
        return AdminUserDto.fromEntity(saved);
    }

    @Override
    public AdminUserDto updateUser(Long id, UpdateUserRequest req) {
        User u = userService.findById(id);
        if (u == null) return null;
        if (req.getEnabled() != null) {
            u.setStatus(req.getEnabled() ? "ACTIVE" : "DISABLED");
        }
        if (req.getRoles() != null && !req.getRoles().isEmpty()) {
            // 取第一个角色作为主角色
            String first = req.getRoles().iterator().next();
            u.setRole(first);
        }
        User saved = userService.saveUser(u);
        return AdminUserDto.fromEntity(saved);
    }

    @Override
    public boolean deleteUser(Long id) {
        User u = userService.findById(id);
        if (u == null) return false;
        userService.deleteById(id);
        return true;
    }
}
