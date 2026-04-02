package org.example.auction.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.CategoryStatsDto;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理员 - 数据统计", description = "管理员查看运营统计数据")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStatsController {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final BidMapper bidMapper;

    public AdminStatsController(OrderMapper orderMapper, UserMapper userMapper, BidMapper bidMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.bidMapper = bidMapper;
    }

    @Operation(summary = "获取运营统计", description = "返回交易额、订单量、活跃用户和热门品类")
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        BigDecimal totalVolume = orderMapper.sumTotalVolume();
        Long totalTransactions = orderMapper.countTotalTransactions();
        Long soldItems = orderMapper.countDistinctSoldItems();
        Long activeUsers = userMapper.countActiveUsers();
        Long biddedItems = bidMapper.countDistinctBidItems();
        List<CategoryStatsDto> categories = orderMapper.countOrdersByCategory();

        BigDecimal safeTotalVolume = totalVolume == null ? BigDecimal.ZERO : totalVolume;
        long safeTotalTransactions = totalTransactions == null ? 0L : totalTransactions;
        long safeSoldItems = soldItems == null ? 0L : soldItems;
        long safeActiveUsers = activeUsers == null ? 0L : activeUsers;
        long safeBiddedItems = biddedItems == null ? 0L : biddedItems;
        List<CategoryStatsDto> safeCategories = categories == null ? List.of() : categories;

        BigDecimal conversionRate = BigDecimal.ZERO;
        if (safeBiddedItems > 0) {
            conversionRate = BigDecimal.valueOf(safeSoldItems)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(safeBiddedItems), 2, RoundingMode.HALF_UP);
            if (conversionRate.compareTo(BigDecimal.valueOf(100)) > 0) {
                conversionRate = BigDecimal.valueOf(100);
            }
        }

        List<Map<String, Object>> popularCategories = new ArrayList<>();
        for (CategoryStatsDto category : safeCategories) {
            Long rawCount = category.getCount();
            Long count = rawCount != null ? rawCount : 0L;

            int percentage = 0;
            if (safeTotalTransactions > 0) {
                percentage = BigDecimal.valueOf(count)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(safeTotalTransactions), 0, RoundingMode.HALF_UP)
                        .intValue();
            }

            Map<String, Object> item = new HashMap<>();
            item.put("name", category.getName());
            item.put("count", count);
            item.put("percentage", percentage);
            popularCategories.add(item);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalVolume", safeTotalVolume);
        stats.put("totalTransactions", safeTotalTransactions);
        stats.put("activeUsers", safeActiveUsers);
        stats.put("conversionRate", conversionRate);
        stats.put("popularCategories", popularCategories);

        return ResponseEntity.ok(ApiResponse.ok(stats));
    }
}
