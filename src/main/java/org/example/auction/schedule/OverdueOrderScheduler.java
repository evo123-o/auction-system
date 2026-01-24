package org.example.auction.schedule;

import org.example.auction.entity.Order;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.service.BreachService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 扫描逾期未支付订单并执行违约处理
 */
@Component
public class OverdueOrderScheduler {

    private final OrderMapper orderMapper;
    private final BreachService breachService;

    public OverdueOrderScheduler(OrderMapper orderMapper, BreachService breachService) {
        this.orderMapper = orderMapper;
        this.breachService = breachService;
    }

    @Scheduled(cron = "0 */5 * * * *") // 每5分钟
    public void scanOverdueOrders() {
        List<Order> overdue = orderMapper.findOverdueUnpaid();
        for (Order o : overdue) {
            breachService.handleBreach(o);
        }
    }
}
