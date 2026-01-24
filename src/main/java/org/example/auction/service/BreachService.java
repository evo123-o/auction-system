package org.example.auction.service;

import org.example.auction.entity.BreachRecord;
import org.example.auction.entity.Order;

import java.util.List;

public interface BreachService {

    /**
     * 对逾期未支付订单执行违约处理（罚没保证金、扣减信用分）
     */
    void handleBreach(Order order);

    List<BreachRecord> listUserBreaches(Long userId);
}