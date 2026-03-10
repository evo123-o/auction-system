package org.example.auction.service;

import org.example.auction.entity.BreachRecord;
import org.example.auction.entity.Order;

import java.util.List;

public interface BreachService {

    /**
     * 对逾期未支付订单执行违约处理（罚没保证金、扣减信用分）
     */
    void handleBreach(Order order);

    /**
     * 发货超时惩罚（卖家未按时发货）
     */
    void handleShippingBreach(Order order);

    /**
     * 撤销发货超时惩罚（管理员）
     */
    void revokeShippingBreach(Order order, Long adminUserId);

    void revokeBreach(Long orderId);

    List<BreachRecord> listUserBreaches(Long userId);

    List<BreachRecord> listOrderBreaches(Long orderId);
}