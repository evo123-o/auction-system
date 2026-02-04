package org.example.auction.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

/**
 * 创建用户请求
 */
@Setter
@Getter
public class CreateUserRequest {
    private String username;
    private String password;
    private String email;
    private Set<String> roles;

}
