package org.example.auction.security;

import org.example.auction.entity.User;
import org.example.auction.service.UserService;
import org.example.auction.util.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 可注入的 CurrentUserService：通过 SecurityUtils 读取当前 username，再使用 UserService 查找 userId。
 * 避免在静态工具中注入 Bean 导致 NullPointerException。
 */
@Service
public class CurrentUserService {
    private final UserService userService;

    public CurrentUserService(UserService userService) {
        this.userService = userService;
    }

    public Optional<Long> getCurrentUserId() {
        return SecurityUtils.getCurrentUsername().flatMap(username -> {
            User u = userService.findByUsername(username);
            return u == null ? Optional.empty() : Optional.ofNullable(u.getId());
        });
    }
}