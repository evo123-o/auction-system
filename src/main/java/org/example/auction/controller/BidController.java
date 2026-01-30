package org.example.auction.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.PlaceBidRequest;
import org.example.auction.entity.Bid;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.BidService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * BidController：提供出价接口与出价历史
 * - POST /api/bids/place            body: { itemId, amount }
 * - POST /api/items/{id}/bid       body: { amount }  (兼容路由)
 * - GET  /api/bids/history?itemId  查询某拍品出价历史
 */
@RestController
@RequestMapping("/api")
public class BidController {

    private final BidService bidService;
    private final CurrentUserService currentUserService;

    public BidController(BidService bidService, CurrentUserService currentUserService) {
        this.bidService = bidService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/bids/place")
    public ResponseEntity<?> placeBid(@Valid @RequestBody PlaceBidRequest req) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }
        Long userId = optUser.get();
        try {
            Bid bid = bidService.placeBid(userId, req.getItemId(), req.getAmount());
            return ResponseEntity.ok(ApiResponse.ok(bid));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务器内部错误: " + ex.getMessage()));
        }
    }

    @PostMapping("/items/{id}/bid")
    public ResponseEntity<?> placeBidOnItem(@PathVariable("id") Long itemId, @Valid @RequestBody AmountOnly amt) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }
        Long userId = optUser.get();
        try {
            Bid bid = bidService.placeBid(userId, itemId, amt.getAmount());
            return ResponseEntity.ok(ApiResponse.ok(bid));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("服务器内部错误: " + ex.getMessage()));
        }
    }

    @GetMapping("/bids/history")
    public ResponseEntity<?> history(@RequestParam("itemId") Long itemId) {
        Optional<Long> optUser = currentUserService.getCurrentUserId();
        if (optUser.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        // 一般允许任何登录用户查看某拍品的出价历史；如需仅限创建者或管理员可查，可加权限判断
        List<Bid> list = bidService.listByItem(itemId);
        return ResponseEntity.ok(ApiResponse.ok(list));
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
