package org.example.auction.service;

import org.example.auction.entity.Logistics;

/**
 * 物流服务接口
 */
public interface LogisticsService {

    /**
     * 获取订单的物流信息
     */
    Logistics getByOrderId(Long orderId);

    /**
     * 创建或更新物流信息（管理员使用）
     */
    Logistics saveOrUpdate(Long orderId, String company, String trackingNo, String notes);
}
