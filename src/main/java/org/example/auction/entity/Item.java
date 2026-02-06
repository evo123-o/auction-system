package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * items 表对应实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("items")
public class Item {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String title;

    private String category;

    private String description;

    @TableField("start_price")
    private BigDecimal startPrice;

    @TableField("current_price")
    private BigDecimal currentPrice;

    @TableField("deposit_amount")
    private BigDecimal depositAmount;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private String status; // PENDING / ON_SHELF / RUNNING / SOLD / CLOSED / REJECTED

    @TableField("extend_count")
    private Integer extendCount;

    @TableField("max_extend")
    private Integer maxExtend;

    @TableField("image_path")
    private String imagePath;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField("auto_extension")
    private Boolean autoExtension;

}