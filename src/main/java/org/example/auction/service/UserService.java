package org.example.auction.service;

import org.example.auction.dto.RegisterRequest;
import org.example.auction.entity.User;

public interface UserService {
    /**
     * 根据用户名查用户（可以返回 null）
     */
    User findByUsername(String username);

    /**
     * 注册新用户（会加密密码并插入）
     */
    User register(RegisterRequest req);

    /**
     * 根据ID查询用户
     * @param id 用户ID
     * @return 用户实体，如果未找到则返回 null
     */
    User findById(Long id);
}