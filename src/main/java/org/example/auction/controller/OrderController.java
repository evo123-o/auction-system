package org.example.auction.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.PageResponse;
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

    /**
     * 获取订单详情
     */
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
     * 分页查询我的订单（作为买家）
     */
    @GetMapping("/my/buyer")
    public ResponseEntity<?> myBuyerOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));

        Page<Order> pg = new Page<>(page, size);
        IPage<Order> results = orderService.pageByBuyer(pg, optUserId.get(), status);

        PageResponse<Order> resp = PageResponse.<Order>builder()
                .total(results.getTotal())
                .pages(results.getPages())
                .current(page)
                .size(size)
                .records(results.getRecords())
                .build();
        return ResponseEntity.ok(resp);
    }

    /**
     * 分页查询我的订单（作为卖家）
     */
    @GetMapping("/my/seller")
    public ResponseEntity<?> mySellerOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));

        Page<Order> pg = new Page<>(page, size);
        IPage<Order> results = orderService.pageBySeller(pg, optUserId.get(), status);

        PageResponse<Order> resp = PageResponse.<Order>builder()
                .total(results.getTotal())
                .pages(results.getPages())
                .current(page)
                .size(size)
                .records(results.getRecords())
                .build();
        return ResponseEntity.ok(resp);
    }

    /**
     * 管理员查询所有订单
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> allOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));

        if (!SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅管理员可访问"));
        }

        Page<Order> pg = new Page<>(page, size);
        IPage<Order> results = orderService.pageAll(pg, status);

        PageResponse<Order> resp = PageResponse.<Order>builder()
                .total(results.getTotal())
                .pages(results.getPages())
                .current(page)
                .size(size)
                .records(results.getRecords())
                .build();
        return ResponseEntity.ok(resp);
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

    /**
     * 标记订单为已发货（卖家或管理员）
     */
    @PostMapping("/ship/{id}")
    public ResponseEntity<?> ship(@PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        if (!order.getSellerId().equals(optUserId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅卖家或管理员可发货"));
        }
        try {
            Order shipped = orderService.markShipped(id);
            return ResponseEntity.ok(ApiResponse.ok(shipped));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }

    /**
     * 确认收货（买家）
     */
    @PostMapping("/receive/{id}")
    public ResponseEntity<?> receive(@PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        if (!order.getBuyerId().equals(optUserId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅买家可确认收货"));
        }
        try {
            Order received = orderService.markReceived(id);
            return ResponseEntity.ok(ApiResponse.ok(received));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(e.getMessage()));
        }
    }

    /**
     * 获取订单的 HTML 凭证
     */
    @GetMapping("/{id}/receipt")
    public ResponseEntity<?> getReceipt(@PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));

        // 权限：买家、卖家或管理员可查
        if (!order.getBuyerId().equals(optUserId.get())
                && !order.getSellerId().equals(optUserId.get())
                && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权查看该订单凭证"));
        }

        String receiptPath = orderService.generateReceiptHtml(order);
        if (receiptPath == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("凭证生成失败"));
        }
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("receiptPath", receiptPath)));
    }
}
