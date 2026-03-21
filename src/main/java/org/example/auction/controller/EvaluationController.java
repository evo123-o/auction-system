package org.example.auction.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Evaluation;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.UserMapper;
import org.example.auction.security.CurrentUserService;
import org.example.auction.service.EvaluationService;
import org.example.auction.service.OrderService;
import org.example.auction.util.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 评价接口
 */
@RestController
@RequestMapping("/api/evaluations")
@Tag(name = "评价管理", description = "订单评价的创建和查询接口")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final OrderService orderService;
    private final CurrentUserService currentUserService;
        private final UserMapper userMapper;

    public EvaluationController(EvaluationService evaluationService,
                                OrderService orderService,
                    CurrentUserService currentUserService,
                    UserMapper userMapper) {
        this.evaluationService = evaluationService;
        this.orderService = orderService;
        this.currentUserService = currentUserService;
        this.userMapper = userMapper;
    }

        @Operation(summary = "获取拍品评价", description = "获取指定拍品的所有评价，公开可见")
        @GetMapping("/item/{itemId}")
        public ResponseEntity<?> getByItem(@Parameter(description = "拍品ID") @PathVariable Long itemId) {
        List<Evaluation> evaluations = evaluationService.getByItemId(itemId);

        Set<Long> reviewerIds = evaluations.stream()
            .map(Evaluation::getReviewerId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());

        Map<Long, String> reviewerNameMap = reviewerIds.isEmpty()
            ? java.util.Collections.emptyMap()
            : userMapper.selectList(
                new LambdaQueryWrapper<User>()
                    .in(User::getId, reviewerIds)
            ).stream().collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

        List<EvaluationPublicView> result = evaluations.stream()
            .map(e -> new EvaluationPublicView(
                e.getId(),
                e.getOrderId(),
                e.getReviewerId(),
                reviewerNameMap.get(e.getReviewerId()),
                e.getRating(),
                e.getComment(),
                e.getCreatedAt()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(result));
        }

    /**
     * 获取订单的所有评价
     */
    @Operation(summary = "获取订单评价", description = "获取指定订单的所有评价，仅买家、卖家或管理员可查看")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<?> getByOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
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
    @Operation(summary = "我的评价列表", description = "获取当前用户创建的所有评价")
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
    @Operation(summary = "创建评价", description = "对已完成的订单进行评价，仅交易双方可评价")
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
    @Operation(summary = "检查是否已评价", description = "检查当前用户是否已对指定订单进行评价")
    @GetMapping("/check/{orderId}")
    public ResponseEntity<?> checkReviewed(@Parameter(description = "订单ID") @PathVariable Long orderId) {
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

    @Getter
    public static class EvaluationPublicView {
        private final Long id;
        private final Long orderId;
        private final Long reviewerId;
        private final String reviewerName;
        private final Integer rating;
        private final String comment;
        private final java.time.LocalDateTime createdAt;

        public EvaluationPublicView(Long id, Long orderId, Long reviewerId, String reviewerName,
                                    Integer rating, String comment, java.time.LocalDateTime createdAt) {
            this.id = id;
            this.orderId = orderId;
            this.reviewerId = reviewerId;
            this.reviewerName = reviewerName;
            this.rating = rating;
            this.comment = comment;
            this.createdAt = createdAt;
        }
    }
}
