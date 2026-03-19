package org.example.auction.controller;

import java.util.List;
import java.util.Optional;

import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.BreachRecord;
import org.example.auction.entity.Order;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.BreachService;
import org.example.auction.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "管理员 - 订单违约干预", description = "管理员订单违约处理接口")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;
    private final BreachService breachService;
    private final CurrentUserService currentUserService;

    public AdminOrderController(OrderService orderService,
                                BreachService breachService,
                                CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.breachService = breachService;
        this.currentUserService = currentUserService;
    }

    @Operation(summary = "手动触发发货违约")
    @PostMapping("/{id}/force-shipping-breach")
    public ResponseEntity<?> forceShippingBreach(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        try {
            breachService.handleShippingBreach(order);
            Order updated = orderService.getById(id);
            return ResponseEntity.ok(ApiResponse.ok(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }

    @Operation(summary = "撤销发货违约")
    @PostMapping("/{id}/revoke-shipping-breach")
    public ResponseEntity<?> revokeShippingBreach(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        Optional<Long> adminUserId = currentUserService.getCurrentUserId();
        if (adminUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }

        try {
            breachService.revokeShippingBreach(order, adminUserId.get());
            Order updated = orderService.getById(id);
            return ResponseEntity.ok(ApiResponse.ok(updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }

    @Operation(summary = "撤销支付违约")
    @PostMapping("/{id}/revoke-payment-breach")
    public ResponseEntity<?> revokePaymentBreach(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        List<BreachRecord> records = breachService.listOrderBreaches(id);
        if (records == null || records.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("未找到可撤销的违约记录"));
        }

        String reason = records.get(0).getReason();
        if (!("逾期未支付".equals(reason) || "Payment overdue".equals(reason))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("该订单最近违约类型不是支付违约"));
        }

        try {
            breachService.revokeBreach(id);
            Order updated = orderService.getById(id);
            return ResponseEntity.ok(ApiResponse.ok(updated));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }

    @Operation(summary = "撤销违约（通用）")
    @PostMapping({"/{id}/revoke-breach", "/{id}/breach/revoke"})
    public ResponseEntity<?> revokeBreach(@PathVariable Long id) {
        Order order = orderService.getById(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        try {
            breachService.revokeBreach(id);
            Order updated = orderService.getById(id);
            return ResponseEntity.ok(ApiResponse.ok(updated));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }
}
