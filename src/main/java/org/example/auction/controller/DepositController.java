package org.example.auction.controller;

import jakarta.validation.constraints.NotNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Item;
import org.example.auction.service.DepositService;
import org.example.auction.service.ItemService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 保证金接口：创建/模拟支付/状态查询
 */
@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService depositService;
    private final ItemService itemService;

    public DepositController(DepositService depositService, ItemService itemService) {
        this.depositService = depositService;
        this.itemService = itemService;
    }

    @PostMapping("/init/{itemId}")
    public ResponseEntity<?> init(@PathVariable Long itemId) {
        Optional<Long> userId = SecurityUtils.getCurrentUserId(null); // 若你在 SecurityUtils 依赖 UserService，可改为注入并传入
        if (userId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
        Item item = itemService.getById(itemId);
        if (item == null) return ResponseEntity.status(404).body(ApiResponse.fail("拍品不存在"));
        BigDecimal amount = item.getDepositAmount() == null ? BigDecimal.ZERO : item.getDepositAmount();
        Deposit d = depositService.createOrUpdate(userId.get(), itemId, amount);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("depositId", d.getId(), "status", d.getStatus(), "amount", d.getAmount())));
    }

    @PostMapping("/pay/{depositId}")
    public ResponseEntity<?> pay(@PathVariable Long depositId) {
        // 模拟支付：生成随机支付单号
        String paymentRef = "PAY-" + UUID.randomUUID();
        Deposit d = depositService.markPaid(depositId, paymentRef);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("depositId", d.getId(), "status", d.getStatus(), "paymentRef", d.getPaymentRef())));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam @NotNull Long itemId, @RequestParam @NotNull Long userId) {
        Item item = itemService.getById(itemId);
        BigDecimal required = item == null ? BigDecimal.ZERO : item.getDepositAmount();
        boolean ok = depositService.isEligibleForBidding(userId, itemId, required);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("eligible", ok)));
    }
}
