package org.example.auction.dto;

import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 请求出价 DTO
 */
@Data
public class PlaceBidRequest {
    @NotNull
    private Long itemId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;
}