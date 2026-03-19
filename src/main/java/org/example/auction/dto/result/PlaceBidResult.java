package org.example.auction.dto.result;

import java.time.LocalDateTime;

import org.example.auction.entity.Bid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceBidResult {

    private Bid bid;
    private boolean extended;
    private LocalDateTime newEndTime;
    private Integer extendCount;
}
