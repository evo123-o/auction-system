package org.example.auction.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 对外返回的拍品信息 DTO（不包含敏感信息）
 */
@Data
@Builder
public class ItemDTO {
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
    private String imageUrl; // 可直接用于前端展示
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
