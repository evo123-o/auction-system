package org.example.auction.dto;

import lombok.Getter;
import lombok.Setter;
import org.example.auction.entity.User;

import java.time.LocalDateTime;

/**
 * 管理员视图的用户 DTO
 */
@Setter
@Getter
public class AdminUserDto {
    // getters and setters
    private Long id;
    private String username;
    private String email;
    private String role; // 单一角色
    private Integer creditScore;
    private Boolean enabled; // map from status
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminUserDto fromEntity(User u) {
        if (u == null) return null;
        AdminUserDto d = new AdminUserDto();
        d.id = u.getId();
        d.username = u.getUsername();
        d.email = u.getEmail();
        d.role = u.getRole();
        d.creditScore = u.getCreditScore();
        d.enabled = "ACTIVE".equalsIgnoreCase(u.getStatus());
        d.createdAt = u.getCreatedAt();
        d.updatedAt = u.getUpdatedAt();
        return d;
    }

}
