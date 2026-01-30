package org.example.auction.service;

import org.example.auction.entity.User;

public interface UserService {
    /**
     * 根据用户名查用户（可以返回 null）
     */
    User findByUsername(String username);

    /**
     * 根据用户名或邮箱查用户（可以返回 null）
     */
    User findByUsernameOrEmail(String usernameOrEmail);

    /**
     * 注册新用户（会加密密码并插入）
     */
    User register(String username, String rawPassword, String email);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户实体，如果未找到则返回 null
     */
    User findById(Long id);

    void update(User user);
}