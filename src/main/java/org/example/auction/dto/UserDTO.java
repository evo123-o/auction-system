package org.example.auction.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 对外返回的用户信息 DTO（不包含密码）
 */
@Data
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private Integer creditScore;
    private String status;
}
