package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * refresh_tokens 表的实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("refresh_tokens")
public class RefreshToken {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String token;

    private LocalDateTime expiresAt;

    private Boolean revoked;

    private LocalDateTime createdAt;
}
