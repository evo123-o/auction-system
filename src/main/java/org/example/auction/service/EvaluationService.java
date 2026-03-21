package org.example.auction.service;

import java.util.List;

import org.example.auction.entity.Evaluation;

/**
 * 评价服务接口
 */
public interface EvaluationService {

    /**
     * 获取订单的所有评价
     */
    List<Evaluation> getByOrderId(Long orderId);

    /**
     * 获取用户的所有评价
     */
    List<Evaluation> getByReviewerId(Long reviewerId);

    /**
     * 获取拍品的所有评价
     */
    List<Evaluation> getByItemId(Long itemId);

    /**
     * 创建评价
     */
    Evaluation create(Long orderId, Long reviewerId, Integer rating, String comment);

    /**
     * 检查用户是否已对订单评价
     */
    boolean hasReviewed(Long orderId, Long reviewerId);
}
