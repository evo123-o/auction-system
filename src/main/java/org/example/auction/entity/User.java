package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String password; // 存储 BCrypt 哈希
    private String email;
    private String role;
    private Integer creditScore;
    private String status;
    private LocalDateTime createdAt;
}
