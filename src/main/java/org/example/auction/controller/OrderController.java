package org.example.auction.controller;

import java.util.Map;
import java.util.Optional;

import org.example.auction.dto.ApiResponse;
import org.example.auction.dto.PageResponse;
import org.example.auction.entity.Order;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.BreachService;
import org.example.auction.service.OrderService;
import org.example.auction.service.PaymentService;
import org.example.auction.service.ReceiptService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 订单接口：详情查询与模拟支付
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "订单管理", description = "订单查询、支付、发货、收货等接口")
public class OrderController {

    private final OrderService orderService;
    private final CurrentUserService currentUserService;
    private final ReceiptService receiptService;
    private final BreachService breachService;
    private final PaymentService paymentService;

    public OrderController(OrderService orderService, CurrentUserService currentUserService, ReceiptService receiptService, BreachService breachService, PaymentService paymentService) {
        this.receiptService = receiptService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
        this.breachService = breachService;
        this.paymentService = paymentService;
    }

    /**
     * 统一查询订单列表（买家/卖家）
     */
    @Operation(summary = "查询订单列表", description = "根据视图参数查询买家或卖家的订单列表")
    @GetMapping
    public ResponseEntity<?> list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status,
            @Parameter(description = "是否卖家视图") @RequestParam(defaultValue = "false") boolean sellerView) {

        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));

        Page<Order> pg = new Page<>(page, size);
        IPage<Order> results;

        if (sellerView) {
            results = orderService.pageBySeller(pg, optUserId.get(), status);
        } else {
            results = orderService.pageByBuyer(pg, optUserId.get(), status);
        }

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
     * 获取订单详情
     */
    @Operation(summary = "获取订单详情", description = "根据订单ID获取订单详情，仅买家、卖家或管理员可查看")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@Parameter(description = "订单ID") @PathVariable Long id) {
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

        // 附加违约信息（如果订单处于BREACH状态，或者曾经有违约记录，都可以查）
        // 这里简单点，无论状态如何，都尝试加载违约记录
        java.util.List<org.example.auction.entity.BreachRecord> breaches = breachService.listOrderBreaches(id);
        if (breaches != null && !breaches.isEmpty()) {
            order.setBreachRecords(breaches);
            // 取最早或最晚的违约时间作为 breachedAt
            // 根据需求，可能是第一次违约时间
            order.setBreachedAt(breaches.get(0).getCreatedAt());
        }

        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    /**
     * 分页查询我的订单（作为买家）
     */
    @Operation(summary = "查询我的订单（买家）", description = "分页查询当前用户作为买家的订单列表")
    @GetMapping("/my/buyer")
    public ResponseEntity<?> myBuyerOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status) {
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
    @Operation(summary = "查询我的订单（卖家）", description = "分页查询当前用户作为卖家的订单列表")
    @GetMapping("/my/seller")
    public ResponseEntity<?> mySellerOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status) {
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
    @Operation(summary = "查询所有订单（管理员）", description = "管理员分页查询所有订单列表")
    @GetMapping("/admin/all")
    public ResponseEntity<?> allOrders(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "订单状态") @RequestParam(required = false) String status) {
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
    * 发起支付宝沙箱支付（异步回调成功后置为 PAID）
     */
    @Operation(summary = "支付订单", description = "发起支付宝沙箱支付，仅买家或管理员可操作")
    @PostMapping("/pay/{id}")
    public ResponseEntity<?> pay(@Parameter(description = "订单ID") @PathVariable Long id) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        Order order = orderService.getById(id);
        if (order == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        if (!order.getBuyerId().equals(optUserId.get()) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅买家或管理员可支付订单"));
        }
        Map<String, String> payInfo = paymentService.createOrderPayInfo(order);
        return ResponseEntity.ok(ApiResponse.ok(payInfo));
    }

    /**
     * 标记订单为已发货（卖家或管理员）
     */
    @Operation(summary = "订单发货", description = "标记订单为已发货，仅卖家或管理员可操作")
    @PostMapping("/ship/{id}")
    public ResponseEntity<?> ship(@Parameter(description = "订单ID") @PathVariable Long id) {
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
    @Operation(summary = "确认收货", description = "买家确认收货，仅买家或管理员可操作")
    @PostMapping("/receive/{id}")
    public ResponseEntity<?> receive(@Parameter(description = "订单ID") @PathVariable Long id) {
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
    @Operation(summary = "获取订单凭证", description = "获取订单的HTML凭证，仅买家、卖家或管理员可查看")
    @GetMapping(value = "/{id}/receipt", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> getReceipt(@Parameter(description = "订单ID") @PathVariable Long id) {
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

        // 调用生成 HTML 内容的方法
        String htmlContent = orderService.generateReceiptHtml(order);
        if (htmlContent == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("凭证生成失败"));
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_TYPE, "text/html;charset=UTF-8")
                .body(htmlContent);
    }
    @GetMapping("/{orderId}/receipt/pdf")
    public ResponseEntity<byte[]> exportReceiptPdf(@PathVariable Long orderId) {
        byte[] pdfBytes = receiptService.generatePdf(orderId);

        if (pdfBytes == null || pdfBytes.length == 0) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new byte[0]);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
                .filename("order_" + orderId + "_receipt.pdf").build());

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
