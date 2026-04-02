package org.example.auction.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * bids 表对应实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("bids")
public class Bid {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private Long userId;

    private BigDecimal amount;

    @TableField("bid_time")
    private LocalDateTime bidTime;
}
