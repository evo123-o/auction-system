package org.example.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 分类统计数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryStatsDto {
    private String category;
    private Long count;
    private BigDecimal totalAmount;
}
