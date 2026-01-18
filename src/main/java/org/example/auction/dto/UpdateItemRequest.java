package org.example.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 更新拍品请求 DTO
 */
@Data
public class UpdateItemRequest {

    @NotBlank
    private String title;

    private String category;

    private String description;

    private BigDecimal startPrice;

    private BigDecimal depositAmount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;
}
