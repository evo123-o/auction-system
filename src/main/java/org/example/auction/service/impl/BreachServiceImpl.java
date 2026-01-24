package org.example.auction.service.impl;

import org.example.auction.entity.BreachRecord;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.BreachRecordMapper;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.BreachService;
import org.example.auction.service.DepositService;
import org.example.auction.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BreachServiceImpl implements BreachService {

    private final BreachRecordMapper breachRecordMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final DepositService depositService;
    private final NotificationService notificationService;

    public BreachServiceImpl(BreachRecordMapper breachRecordMapper,
                             OrderMapper orderMapper,
                             UserMapper userMapper,
                             DepositService depositService,
                             NotificationService notificationService) {
        this.breachRecordMapper = breachRecordMapper;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.depositService = depositService;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void handleBreach(Order order) {
        // 罚没保证金
        depositService.forfeit(order.getBuyerId(), order.getItemId());

        // 扣减信用分（示例：扣 10 分）
        User buyer = userMapper.selectById(order.getBuyerId());
        if (buyer != null) {
            int cs = buyer.getCreditScore();
            buyer.setCreditScore(Math.max(0, cs - 10));
            userMapper.updateById(buyer);
        }

        // 记录违约
        BreachRecord br = BreachRecord.builder()
                .userId(order.getBuyerId())
                .itemId(order.getItemId())
                .orderId(order.getId())
                .reason("Payment overdue")
                .penaltyAmount(BigDecimal.ZERO) // 如需记录罚金金额，可设置为保证金金额
                .creditScoreDelta(-10)
                .createdAt(LocalDateTime.now())
                .build();
        breachRecordMapper.insert(br);

        // 更新订单状态
        order.setStatus("BREACH");
        orderMapper.updateById(order);

        // 通知双方
        notificationService.notifyUser(order.getBuyerId(), "Order " + order.getId() + " marked as BREACH, deposit forfeited.");
        notificationService.notifyUser(order.getSellerId(), "Buyer breached for order " + order.getId() + ".");
    }

    @Override
    public List<BreachRecord> listUserBreaches(Long userId) {
        return breachRecordMapper.listByUser(userId);
    }
}
