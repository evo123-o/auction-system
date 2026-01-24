package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保证金记录
 * 状态：PENDING(待支付) / PAID(已支付可参与) / FROZEN(已冻结) / REFUNDED(已退款) / FORFEITED(已罚没)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("deposits")
public class Deposit {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long userId;

    private BigDecimal amount;

    private String status;

    private String paymentRef;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
