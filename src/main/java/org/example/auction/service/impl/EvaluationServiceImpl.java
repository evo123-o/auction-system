package org.example.auction.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.example.auction.entity.Evaluation;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.EvaluationMapper;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.EvaluationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    public EvaluationServiceImpl(EvaluationMapper evaluationMapper, UserMapper userMapper, OrderMapper orderMapper) {
        this.evaluationMapper = evaluationMapper;
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public List<Evaluation> getByOrderId(Long orderId) {
        return evaluationMapper.findByOrderId(orderId);
    }

    @Override
    public List<Evaluation> getByReviewerId(Long reviewerId) {
        return evaluationMapper.findByReviewerId(reviewerId);
    }

    @Override
    public List<Evaluation> getByItemId(Long itemId) {
        return evaluationMapper.findByItemId(itemId);
    }

    @Override
    @Transactional
    public Evaluation create(Long orderId, Long reviewerId, Integer rating, String comment) {
        // 验证评分范围
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("评分必须在1-5之间");
        }

        // 检查是否已评价
        if (hasReviewed(orderId, reviewerId)) {
            throw new IllegalArgumentException("您已对该订单评价过");
        }

        Evaluation evaluation = Evaluation.builder()
                .orderId(orderId)
                .reviewerId(reviewerId)
                .rating(rating)
                .comment(comment)
                .createdAt(LocalDateTime.now())
                .build();
        evaluationMapper.insert(evaluation);

        // 根据评价结果更新被评价方的信用分
        updateCreditScore(orderId, reviewerId, rating);

        return evaluation;
    }

    @Override
    public boolean hasReviewed(Long orderId, Long reviewerId) {
        LambdaQueryWrapper<Evaluation> qw = new LambdaQueryWrapper<>();
        qw.eq(Evaluation::getOrderId, orderId)
          .eq(Evaluation::getReviewerId, reviewerId);
        return evaluationMapper.selectCount(qw) > 0;
    }

    /**
     * 根据评价更新信用分
     * 好评(4-5星)被评价方+2分
     * 中评(3星)不变
     * 差评(1-2星)被评价方-5分
     */
    private void updateCreditScore(Long orderId, Long reviewerId, Integer rating) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return;
        }

        // 确定被评价方：如果评价者是买家，则被评价方是卖家；反之亦然
        Long reviewedUserId = null;
        if (reviewerId.equals(order.getBuyerId())) {
            reviewedUserId = order.getSellerId();
        } else if (reviewerId.equals(order.getSellerId())) {
            reviewedUserId = order.getBuyerId();
        }

        if (reviewedUserId == null) {
            return;
        }

        User reviewedUser = userMapper.selectById(reviewedUserId);
        if (reviewedUser == null) {
            return;
        }

        Integer currentScoreValue = reviewedUser.getCreditScore();
        int currentScore = currentScoreValue != null ? currentScoreValue : 100;
        int delta = 0;

        if (rating >= 4) {
            // 好评 +2 分
            delta = 2;
        } else if (rating <= 2) {
            // 差评 -5 分
            delta = -5;
        }
        // 中评(3星)不变

        if (delta != 0) {
            int newScore = Math.max(0, Math.min(200, currentScore + delta)); // 限制在 0-200 范围内
            reviewedUser.setCreditScore(newScore);
            userMapper.updateById(reviewedUser);
        }
    }
}
