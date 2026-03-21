package org.example.auction.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.PlaceBidRequest;
import org.example.auction.dto.result.PlaceBidResult;
import org.example.auction.entity.Bid;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.BidService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * BidController：提供出价接口与出价历史
 * - POST /api/bids/place            body: { itemId, amount }
 * - POST /api/items/{id}/bid       body: { amount }  (兼容路由)
 * - GET  /api/bids/history?itemId  查询某拍品出价历史
 */
@RestController
@RequestMapping("/api")
@Tag(name = "竞拍管理", description = "出价、出价历史查询等接口")
public class BidController {

    private final BidService bidService;
    private final CurrentUserService currentUserService;
    private final UserMapper userMapper;

    public BidController(BidService bidService, CurrentUserService currentUserService, UserMapper userMapper) {
        this.bidService = bidService;
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
    }

    @Operation(summary = "出价", description = "对指定拍品进行出价")
    @PostMapping("/bids/place")
    public ResponseEntity<?> placeBid(@Valid @RequestBody PlaceBidRequest req) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }
        Long userId = optUser.get();
        try {
            PlaceBidResult result = bidService.placeBid(userId, req.getItemId(), req.getAmount());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务器内部错误: " + ex.getMessage()));
        }
    }

    @Operation(summary = "出价（路径参数方式）", description = "对指定拍品进行出价的兼容接口")
    @PostMapping("/items/{id}/bid")
    public ResponseEntity<?> placeBidOnItem(@Parameter(description = "拍品ID") @PathVariable("id") Long itemId, @Valid @RequestBody AmountOnly amt) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }
        Long userId = optUser.get();
        try {
            PlaceBidResult result = bidService.placeBid(userId, itemId, amt.getAmount());
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务器内部错误: " + ex.getMessage()));
        }
    }

    @Operation(summary = "查询出价历史", description = "获取指定拍品的所有出价记录")
    @GetMapping("/bids/history")
    public ResponseEntity<?> history(@Parameter(description = "拍品ID") @RequestParam("itemId") Long itemId) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        // 一般允许任何登录用户查看某拍品的出价历史；如需仅限创建者或管理员可查，可加权限判断
        List<Bid> list = bidService.listByItem(itemId);

        Set<Long> userIds = list.stream()
                .map(Bid::getUserId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        Map<Long, String> usernameByUserId;
        if (userIds.isEmpty()) {
            usernameByUserId = Collections.emptyMap();
        } else {
            usernameByUserId = userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, userIds))
                    .stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
        }

        List<BidHistoryItem> result = list.stream()
                .map(bid -> new BidHistoryItem(
                        bid.getId(),
                        bid.getItemId(),
                        bid.getUserId(),
                        usernameByUserId.get(bid.getUserId()),
                        bid.getAmount(),
                        bid.getBid_time(),
                        bid.getBid_time()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @Getter
    public static class BidHistoryItem {
        private final Long id;
        private final Long itemId;
        private final Long userId;
        private final String username;
        private final BigDecimal amount;
        private final LocalDateTime bidTime;
        private final LocalDateTime bid_time;

        public BidHistoryItem(Long id, Long itemId, Long userId, String username,
                              BigDecimal amount, LocalDateTime bidTime, LocalDateTime bid_time) {
            this.id = id;
            this.itemId = itemId;
            this.userId = userId;
            this.username = username;
            this.amount = amount;
            this.bidTime = bidTime;
            this.bid_time = bid_time;
        }
    }

    // 内部简单 DTO 用于 /items/{id}/bid 路由
    @Setter
    @Getter
    public static class AmountOnly {
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal amount;

    }
}
