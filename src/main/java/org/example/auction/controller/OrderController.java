package org.example.auction.controller;

import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Order;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.OrderService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 订单接口：详情查询与模拟支付
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService) {
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        // 权限：买家、卖家或管理员可查
        if (!order.getBuyerId().equals(optUserId.get())
                && !order.getSellerId().equals(optUserId.get())
                && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权查看该订单"));
        }
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    /**
     * 模拟支付订单：置为 PAID，并在服务内部做解冻/扣款等逻辑
     */
    @PostMapping("/pay/{id}")
    public ResponseEntity<?> pay(@PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        if (!order.getBuyerId().equals(optUserId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅买家或管理员可支付订单"));
        }
        Order paid = orderService.markPaid(id);
        return ResponseEntity.ok(ApiResponse.ok(paid));
    }
}
