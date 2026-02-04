package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@TableName("password_reset")
public class PasswordResetToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String code;          // 六位验证码
    private String token;         // 可选：随机token（如需要通过链接跳转）
    private LocalDateTime expiresAt;
    private Boolean used;
    private LocalDateTime createdAt;

}