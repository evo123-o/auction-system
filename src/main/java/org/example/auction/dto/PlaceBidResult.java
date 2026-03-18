package org.example.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.auction.entity.Bid;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 竞拍结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBidResult {
    private boolean success;
    private String message;
    private BigDecimal currentPrice;
    private Long bidId;
    private Bid bid;
    private boolean extended;
    private LocalDateTime newEndTime;
    private int extendCount;
}
