package org.example.auction.controller;

import jakarta.validation.constraints.NotNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Item;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.DepositService;
import org.example.auction.service.ItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
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

    /**
     * 初始化保证金记录（准备缴纳）
     */
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

    /**
     * 模拟支付保证金
     */
    @PostMapping("/pay/{depositId}")
    public ResponseEntity<?> pay(@PathVariable Long depositId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));

        // 验证保证金属于当前用户
        Deposit deposit = depositService.getById(depositId);
        if (deposit == null) return ResponseEntity.status(404).body(ApiResponse.fail("保证金记录不存在"));
        if (!deposit.getUserId().equals(optUserId.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权操作该保证金"));
        }

        // 模拟支付：生成随机支付单号
        String paymentRef = "PAY-" + UUID.randomUUID();
        Deposit d = depositService.markPaid(depositId, paymentRef);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("depositId", d.getId(), "status", d.getStatus(), "paymentRef", d.getPaymentRef())));
    }

    /**
     * 查询用户对某拍品的保证金状态
     */
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

    /**
     * 获取当前用户的所有保证金记录
     */
    @GetMapping("/my")
    public ResponseEntity<?> myDeposits() {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));

        List<Deposit> deposits = depositService.listByUser(optUserId.get());
        return ResponseEntity.ok(ApiResponse.ok(deposits));
    }

    /**
     * 获取保证金详情
     */
    @GetMapping("/{depositId}")
    public ResponseEntity<?> getDeposit(@PathVariable Long depositId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(401).body(ApiResponse.fail("未登录"));

        Deposit deposit = depositService.getById(depositId);
        if (deposit == null) return ResponseEntity.status(404).body(ApiResponse.fail("保证金记录不存在"));

        // 仅允许查看自己的保证金记录
        if (!deposit.getUserId().equals(optUserId.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权查看该保证金记录"));
        }

        return ResponseEntity.ok(ApiResponse.ok(deposit));
    }
}
