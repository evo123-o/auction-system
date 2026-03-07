package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.auction.entity.BreachRecord;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.BreachRecordMapper;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.BreachService;
import org.example.auction.service.DepositService;
import org.example.auction.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BreachServiceImpl implements BreachService {

    private static final Logger log = LoggerFactory.getLogger(BreachServiceImpl.class);

    private final BreachRecordMapper breachRecordMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    private final DepositService depositService;
    private final NotificationService notificationService;

    @Value("${app.auction.breach.deduction.step1:10}")
    private int deductionStep1;
    @Value("${app.auction.breach.deduction.step2:15}")
    private int deductionStep2;
    @Value("${app.auction.breach.deduction.step3:20}")
    private int deductionStep3;

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
        if (order == null || order.getId() == null) {
            log.warn("handleBreach called with null order");
            return;
        }

        // 防重复处罚 - 使用 UPDATE 语句判断是否有受影响行
        // UPDATE orders SET status = 'BREACH' WHERE id = ? AND status != 'BREACH'
        // 注意：这会将状态更新提前到业务逻辑的最开始。如果后续逻辑抛异常，事务回滚，状态也会回滚
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, "BREACH")
                .eq(Order::getId, order.getId())
                .ne(Order::getStatus, "BREACH")
        );

        if (rows == 0) {
            log.info("Order {} already processed or marked as BREACH (concurrent update prevented).", order.getId());
            return;
        }

        // 更新内存中的对象状态以供后续逻辑使用
        order.setStatus("BREACH");

        Long buyerId = order.getBuyerId();
        Long itemId = order.getItemId();

        if (buyerId == null) {
            log.warn("order {} has null buyerId, skip breach handling", order.getId());
            return;
        }

        // 先读取保证金信息以便记录罚没金额（在调用 forfeit 之前读取）
        Deposit deposit = null;
        try {
            deposit = depositService.findByItemAndUser(itemId, buyerId);
        } catch (Exception e) {
            log.warn("failed to read deposit for item {} user {}: {}", itemId, buyerId, e.getMessage());
        }
        BigDecimal penaltyAmount = (deposit == null || deposit.getAmount() == null) ? BigDecimal.ZERO : deposit.getAmount();

        // 1) 罚没保证金（先执行业务）
        try {
            depositService.forfeit(buyerId, itemId);
        } catch (Exception e) {
            log.warn("forfeit failed for order {} item {} user {}: {}", order.getId(), itemId, buyerId, e.getMessage());
            // continue: still record breach and deduct credit
        }

        // 2) 计算并扣减信用分（阶梯惩罚）
        User buyer = userMapper.selectById(buyerId);
        int deduction = calculateCreditDeduction(buyerId); // 10/15/20
        if (buyer != null) {
            int current = buyer.getCreditScore() == null ? 0 : buyer.getCreditScore();
            buyer.setCreditScore(Math.max(0, current - deduction));
            userMapper.updateById(buyer);
        }

        // 3) 记录违约
        BreachRecord br = BreachRecord.builder()
                .userId(buyerId)
                .itemId(itemId)
                .orderId(order.getId())
                .reason("Payment overdue")
                .penaltyAmount(penaltyAmount)
                .creditScoreDelta(-deduction)
                .createdAt(LocalDateTime.now())
                .build();
        breachRecordMapper.insert(br);

        // 4) 更新订单状态 (已在开头完成更新，此处移除原来的 updateById 调用)
        // order.setStatus("BREACH");
        // orderMapper.updateById(order);

        // 5) 通知
        notificationService.notifyUser(
                buyerId,
                String.format("Order %d is BREACH. Deposit forfeited: %s, credit -%d.", order.getId(), penaltyAmount, deduction)
        );
        notificationService.notifyUser(
                order.getSellerId(),
                "Buyer breached for order " + order.getId() + "."
        );
    }

    private int calculateCreditDeduction(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Long count;
        try {
            count = breachRecordMapper.countByUserSince(userId, since);
        } catch (Exception e) {
            // fallback to loading list
            List<BreachRecord> records = breachRecordMapper.listByUser(userId);
            count = records == null ? 0 : records.stream()
                    .filter(r -> r.getCreatedAt() != null && r.getCreatedAt().isAfter(since))
                    .count();
        }

        if (count == 0) return deductionStep1;   // 0 times breach recently -> default deduction
        if (count == 1) return deductionStep2;   // 1 time -> increase deduction
        return deductionStep3;                   // >= 2 times -> max deduction
    }

    @Override
    public List<BreachRecord> listUserBreaches(Long userId) {
        return breachRecordMapper.listByUser(userId);
    }
}
