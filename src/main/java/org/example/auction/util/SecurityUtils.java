package org.example.auction.util;

import org.example.auction.entity.User;
import org.example.auction.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;
import java.util.Optional;

/**
 * SecurityContext 帮助类：获取当前用户名 / 判断角色 / 根据 UserService 获取当前用户 id
 */
public class SecurityUtils {

    public static Optional<String> getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return Optional.empty();
        return Optional.of(name);
    }

    public static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (Objects.equals(ga.getAuthority(), "ROLE_" + role) || Objects.equals(ga.getAuthority(), role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 根据当前 SecurityContext 中的 username 查询 UserService 获取用户 id（若存在）
     */
    public static Optional<Long> getCurrentUserId(UserService userService) {
        return getCurrentUsername().flatMap(username -> {
            User u = userService.findByUsername(username);
            if (u == null) return Optional.empty();
            return Optional.ofNullable(u.getId());
        });
    }
}
