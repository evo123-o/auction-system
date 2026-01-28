package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.Evaluation;
import org.example.auction.entity.User;
import org.example.auction.mapper.EvaluationMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.EvaluationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EvaluationServiceImpl implements EvaluationService {

    private final EvaluationMapper evaluationMapper;
    private final UserMapper userMapper;

    public EvaluationServiceImpl(EvaluationMapper evaluationMapper, UserMapper userMapper) {
        this.evaluationMapper = evaluationMapper;
        this.userMapper = userMapper;
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
        // 好评(4-5星)加分，差评(1-2星)扣分
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
        // 简化实现：评价影响被评价方（即交易对手）的信用分
        // 这里需要从order获取买卖双方信息，根据reviewerId确定被评价方
        // 暂时简化：仅记录评价，信用分更新在违约模块中处理
        if (rating <= 2) {
            // 差评可选择扣分
            // 需要根据业务需求补充具体逻辑
        }
    }
}
