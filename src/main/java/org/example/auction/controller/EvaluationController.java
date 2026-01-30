package org.example.auction.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Evaluation;
import org.example.auction.entity.Order;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.EvaluationService;
import org.example.auction.service.OrderService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 评价接口
 */
@RestController
@RequestMapping("/api/evaluations")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;

    public EvaluationController(EvaluationService evaluationService,
                                OrderService orderService,
                                CurrentUserService currentUserService) {
        this.evaluationService = evaluationService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
    }

    /**
     * 获取订单的所有评价
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getByOrder(@PathVariable Long orderId) {
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
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("无权查看该订单评价"));
        }

        List<Evaluation> evaluations = evaluationService.getByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.ok(evaluations));
    }

    /**
     * 获取当前用户的所有评价
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyEvaluations() {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }

        List<Evaluation> evaluations = evaluationService.getByReviewerId(optUserId.get());
        return ResponseEntity.ok(ApiResponse.ok(evaluations));
    }

    /**
     * 创建评价
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody EvaluationRequest request) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }
        Long userId = optUserId.get();

        Order order = orderService.getById(request.getOrderId());
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail("订单不存在"));
        }

        // 权限检查：只有买家或卖家可以评价
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.fail("只有交易双方可以评价"));
        }

        // 检查订单状态：只有已完成的订单才能评价
        if (!"PAID".equalsIgnoreCase(order.getStatus())
                && !"SHIPPED".equalsIgnoreCase(order.getStatus())
                && !"RECEIVED".equalsIgnoreCase(order.getStatus())
                && !"CLOSED".equalsIgnoreCase(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail("订单未完成，无法评价"));
        }

        try {
            Evaluation evaluation = evaluationService.create(
                    request.getOrderId(),
                    userId,
                    request.getRating(),
                    request.getComment()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(evaluation));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.fail(ex.getMessage()));
        }
    }

    /**
     * 检查是否已评价
     */
    @GetMapping("/check/{orderId}")
    public ResponseEntity<?> checkReviewed(@PathVariable Long orderId) {
        Optional<Long> optUserId = currentUserService.getCurrentUserId();
        if (optUserId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.fail("未登录"));
        }

        boolean hasReviewed = evaluationService.hasReviewed(orderId, optUserId.get());
        return ResponseEntity.ok(ApiResponse.ok(java.util.Map.of("hasReviewed", hasReviewed)));
    }

    /**
     * 评价请求 DTO
     */
    @Setter
    @Getter
    public static class EvaluationRequest {
        @NotNull(message = "订单ID不能为空")
        private Long orderId;

        @NotNull(message = "评分不能为空")
        @Min(value = 1, message = "评分最低为1")
        @Max(value = 5, message = "评分最高为5")
        private Integer rating;

        private String comment;

    }
}
