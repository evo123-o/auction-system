package org.example.auction.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Logistics;
import org.example.auction.entity.Order;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.LogisticsService;
import org.example.auction.service.OrderService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 物流信息接口
 */
@RestController
@RequestMapping("/api/logistics")
public class LogisticsController {

    private final LogisticsService logisticsService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public LogisticsController(LogisticsService logisticsService,
                               OrderService orderService,
                               CurrentUserService currentUserService) {
        this.logisticsService = logisticsService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    /**
     * 获取订单的物流信息
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<?> get(@PathVariable Long orderId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }

        Order order = orderService.getById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        // 权限检查：买家、卖家或管理员可查
        Long userId = optUserId.get();
        if (!order.getBuyerId().equals(userId)
                && !order.getSellerId().equals(userId)
                && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权查看该订单物流"));
        }

        Logistics logistics = logisticsService.getByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.ok(logistics));
    }

    /**
     * 创建或更新物流信息（仅管理员或卖家）
     */
    @PostMapping("/{orderId}")
    public ResponseEntity<?> saveOrUpdate(@PathVariable Long orderId,
                                          @Valid @RequestBody LogisticsRequest request) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }

        Order order = orderService.getById(orderId);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        // 权限检查：仅卖家或管理员可以录入物流信息
        Long userId = optUserId.get();
        if (!order.getSellerId().equals(userId) && !SecurityUtils.hasRole("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("仅卖家或管理员可录入物流信息"));
        }

        Logistics logistics = logisticsService.saveOrUpdate(
                orderId,
                request.getCompany(),
                request.getTrackingNo(),
                request.getNotes()
        );
        return ResponseEntity.ok(ApiResponse.ok(logistics));
    }

    /**
     * 物流信息请求 DTO
     */
    public static class LogisticsRequest {
        @NotBlank(message = "物流公司不能为空")
        private String company;

        @NotBlank(message = "物流单号不能为空")
        private String trackingNo;

        private String notes;

        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
        public String getTrackingNo() { return trackingNo; }
        public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}
