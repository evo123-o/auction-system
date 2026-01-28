package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 物流信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("logistics")
public class Logistics {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("order_id")
    private Long orderId;

    private String company;

    @TableField("tracking_no")
    private String trackingNo;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    private String notes;
}
