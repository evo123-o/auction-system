package org.example.auction.controller;

import jakarta.validation.constraints.NotNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Item;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.DepositService;
import org.example.auction.service.ItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 保证金接口：创建/模拟支付/状态查询
 * 现在通过 CurrentUserService 获取当前用户 id（避免静态工具传入 null 导致 NPE）。
 */
@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService depositService;
    private final ItemService itemService;
    private final CurrentUserService currentUserService;

    public DepositController(DepositService depositService, ItemService itemService, CurrentUserService currentUserService) {
        this.depositService = depositService;
        this.itemService = itemService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/init/{itemId}")
    public ResponseEntity<?> init(@PathVariable Long itemId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
        Long userId = optUserId.get();

        Item item = itemService.getById(itemId);
        if (item == null) return ResponseEntity.status(404).body(ApiResponse.fail("拍品不存在"));
        BigDecimal amount = item.getDepositAmount() == null ? BigDecimal.ZERO : item.getDepositAmount();
        Deposit d = depositService.createOrUpdate(userId, itemId, amount);
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
    public ResponseEntity<?> status(@RequestParam @NotNull Long itemId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));
        Long userId = optUserId.get();

        Item item = itemService.getById(itemId);
        BigDecimal required = item == null ? BigDecimal.ZERO : item.getDepositAmount();
        boolean ok = depositService.isEligibleForBidding(userId, itemId, required);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("eligible", ok)));
    }
}
