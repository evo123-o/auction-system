package org.example.auction.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 更新用户请求（管理员用）
 */
@Setter
@Getter
public class UpdateUserRequest {
    // getters / setters
    private Set<String> roles;
    private Boolean enabled;

    private String password; // optional, admin can reset password
    private String email;
    private String role;     // single role
    private Integer creditScore;
}
