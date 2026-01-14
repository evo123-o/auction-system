package org.example.auction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Item {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String category;
    private String description;
    private BigDecimal startPrice;
    private BigDecimal currentPrice;
    private BigDecimal depositAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Integer extendCount;
    private Integer maxExtend;
    private String imagePath;
    private Long createdBy;
    private LocalDateTime createdAt;
}
