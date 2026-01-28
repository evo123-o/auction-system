package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 交易评价
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("evaluations")
public class Evaluation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    @TableField("reviewer_id")
    private Long reviewerId;

    private Integer rating; // 1-5 星

    private String comment;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
