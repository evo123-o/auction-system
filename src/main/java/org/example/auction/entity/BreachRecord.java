package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 违约记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("breach_records")
public class BreachRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long itemId;

    private Long orderId;

    private String reason;

    private BigDecimal penaltyAmount;

    private Integer creditScoreDelta;

    private LocalDateTime createdAt;
}