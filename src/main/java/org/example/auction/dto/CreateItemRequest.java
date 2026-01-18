package org.example.auction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建拍品请求 DTO
 */
@Data
public class CreateItemRequest {

    @NotBlank
    private String title;

    private String category;

    private String description;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal startPrice;

    private BigDecimal depositAmount = BigDecimal.ZERO;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
