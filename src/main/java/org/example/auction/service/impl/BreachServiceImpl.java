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

    // 配置项：支付违约（买家）处理
    @Value("${app.breach.payment.credit-deduction:10}")
    private int paymentCreditDeduction;

    /**
     * 对保证金的处理策略：FORFEIT / FREEZE / NONE
     */
    @Value("${app.breach.payment.deposit-action:FORFEIT}")
    private String paymentDepositAction;

    // 配置项：发货违约（卖家）处理
    @Value("${app.breach.shipping.credit-deduction:10}")
    private int shippingCreditDeduction;

    @Value("${app.breach.shipping.deposit-action:NONE}")
    private String shippingDepositAction;

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
        if (order == null) return;

        Long buyerId = order.getBuyerId();
        Long itemId = order.getItemId();

        // 1) 根据配置决定对保证金的处理
        try {
            if ("FORFEIT".equalsIgnoreCase(paymentDepositAction)) {
                depositService.forfeit(buyerId, itemId);
                log.info("Forfeited deposit for buyer {} item {} due to payment breach", buyerId, itemId);
            } else if ("FREEZE".equalsIgnoreCase(paymentDepositAction)) {
                depositService.freeze(buyerId, itemId);
                log.info("Froze deposit for buyer {} item {} due to payment breach", buyerId, itemId);
            } else {
                log.info("No deposit action for payment breach (action={})", paymentDepositAction);
            }
        } catch (Exception e) {
            log.warn("deposit action failed for order {}: {}", order.getId(), e.getMessage());
        }

        // 2) 扣减信用分（阶梯惩罚替换为固定配置值，保留历史计算可选）
        User buyer = userMapper.selectById(buyerId);
        if (buyer != null) {
            int cs = buyer.getCreditScore() == null ? 0 : buyer.getCreditScore();
            buyer.setCreditScore(Math.max(0, cs - paymentCreditDeduction));
            userMapper.updateById(buyer);
        }

        // 3) 记录违约（尝试读取保证金金额用于记录）
        BigDecimal penaltyAmount = BigDecimal.ZERO;
        try {
            Deposit d = depositService.getById(depositIdFor(buyerId, itemId));
            if (d != null && d.getAmount() != null) penaltyAmount = d.getAmount();
        } catch (Exception ignored) {}

        BreachRecord br = BreachRecord.builder()
                .userId(buyerId)
                .itemId(itemId)
                .orderId(order.getId())
                .reason("逾期未支付")
                .penaltyAmount(penaltyAmount)
                .creditScoreDelta(-paymentCreditDeduction)
                .createdAt(LocalDateTime.now())
                .build();
        breachRecordMapper.insert(br);

        // 4) 更新订单状态
        order.setStatus("BREACH");
        orderMapper.updateById(order);

        // 5) 通知
        notificationService.notifyUser(buyerId, String.format("Order %d is BREACH. Deposit action=%s, credit -%d.", order.getId(), paymentDepositAction, paymentCreditDeduction));
        notificationService.notifyUser(order.getSellerId(), "Buyer breached for order " + order.getId() + ".");
    }

    // 新增发货超时惩罚逻辑
    @Override
    @Transactional
    public void handleShippingBreach(Order order) {
        if (order == null || order.getId() == null) return;

        // 幂等性保护：先尝试将订单状态更新为 BREACH（前提：当前状态不是 BREACH）
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .set(Order::getStatus, "BREACH")
                .eq(Order::getId, order.getId())
                .ne(Order::getStatus, "BREACH")
        );

        if (rows == 0) {
            // 已经处理过或并发已标记
            return;
        }

        // 在事务内更新内存对象状态
        order.setStatus("BREACH");

        Long sellerId = order.getSellerId();
        Long itemId = order.getItemId();
        if (sellerId == null) {
            return;
        }

        // 根据配置决定是否对保证金做操作（通常卖家没有保证金，但保留此配置以防扩展）
        try {
            if ("FORFEIT".equalsIgnoreCase(shippingDepositAction)) {
                depositService.forfeit(sellerId, itemId);
                log.info("Forfeited deposit for seller {} item {} due to shipping breach", sellerId, itemId);
            } else if ("FREEZE".equalsIgnoreCase(shippingDepositAction)) {
                depositService.freeze(sellerId, itemId);
                log.info("Froze deposit for seller {} item {} due to shipping breach", sellerId, itemId);
            } else {
                log.info("No deposit action for shipping breach (action={})", shippingDepositAction);
            }
        } catch (Exception e) {
            log.warn("deposit action failed for shipping breach order {}: {}", order.getId(), e.getMessage());
        }

        // 扣减卖家信用分（例如配置值）
        User seller = userMapper.selectById(sellerId);
        int deduction = shippingCreditDeduction;
        if (seller != null) {
            int cur = seller.getCreditScore() == null ? 0 : seller.getCreditScore();
            seller.setCreditScore(Math.max(0, cur - deduction));
            userMapper.updateById(seller);
        }

        // 记录违约（ penaltyAmount 使用 0 或可按需改为读取相关金额）
        BreachRecord br = BreachRecord.builder()
                .userId(sellerId)
                .itemId(itemId)
                .orderId(order.getId())
                .reason("卖家逾期未发货")
                .penaltyAmount(BigDecimal.ZERO)
                .creditScoreDelta(-deduction)
                .createdAt(LocalDateTime.now())
                .build();
        breachRecordMapper.insert(br);

        // 通知双方
        notificationService.notifyUser(sellerId, "Order " + order.getId() + " marked as BREACH for shipping overdue.");
        notificationService.notifyUser(order.getBuyerId(), "Seller failed to ship for order " + order.getId() + ".");
    }

    @Override
    public List<BreachRecord> listUserBreaches(Long userId) {
        return breachRecordMapper.listByUser(userId);
    }

    @Override
    public List<BreachRecord> listOrderBreaches(Long orderId) {
        return breachRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BreachRecord>()
                        .eq(BreachRecord::getOrderId, orderId)
                        .orderByDesc(BreachRecord::getCreatedAt)
        );
    }

    @Override
    @Transactional
    public void revokeShippingBreach(Order order, Long adminUserId) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"BREACH".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("当前订单不是违约状态，无法撤销");
        }

        Long sellerId = order.getSellerId();
        if (sellerId == null) {
            throw new IllegalArgumentException("订单缺少卖家信息，无法撤销");
        }

        // 1) 找到该订单最近一条发货违约记录
        BreachRecord rec = breachRecordMapper.findLatestShippingBreachByOrderId(order.getId());
        if (rec == null) {
            throw new IllegalArgumentException("未找到可撤销的发货违约记录");
        }

        // 2) 回滚卖家信用分（record.creditScoreDelta 通常是负值，如 -10）
        int rollbackScore = rec.getCreditScoreDelta() == null ? 0 : -rec.getCreditScoreDelta();
        if (rollbackScore > 0) {
            User seller = userMapper.selectById(sellerId);
            if (seller != null) {
                int cur = seller.getCreditScore() == null ? 0 : seller.getCreditScore();
                seller.setCreditScore(cur + rollbackScore);
                userMapper.updateById(seller);
            }
        }

        // 3) 订单状态从 BREACH 回滚到 PAID（允许后续正常发货）
        order.setStatus("PAID");
        orderMapper.updateById(order);

        // 4) 删除违约记录（当前采用“硬删除”快速方案）
        breachRecordMapper.deleteByIdHard(rec.getId());

        // 5) 通知
        notificationService.notifyUser(
                sellerId,
                "Order " + order.getId() + " shipping breach has been revoked by admin."
        );
        if (order.getBuyerId() != null) {
            notificationService.notifyUser(
                    order.getBuyerId(),
                    "Shipping breach for order " + order.getId() + " has been revoked by admin."
            );
        }
    }

    /**
     * 撤销违约（通用）
     * 支持撤销支付违约(逾期未支付) 和 发货违约(卖家逾期未发货)
     */
    @Override
    @Transactional
    public void revokeBreach(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        if (!"BREACH".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("当前订单不是违约状态，无法撤销");
        }

        // 查找最近的违约记录
        BreachRecord record = breachRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BreachRecord>()
                        .eq(BreachRecord::getOrderId, orderId)
                        .orderByDesc(BreachRecord::getId)
                        .last("LIMIT 1")
        );

        if (record == null) {
            // 如果没有记录但订单状态是BREACH, 可能是数据不致, 尝试暴力回滚状态
            log.warn("Revoking breach for order {} but no record found. Resetting status to PAID/PENDING_PAYMENT based on logic.", orderId);
            // 这里比较难判断回滚到哪个状态，需要根据 breach 类型猜测
            // 如果之前是支付违约，应回滚到 PENDING_PAYMENT; 如果是发货违约，应回滚到 PAID
            // 简单处理：如果是 "逾期未支付" (通常买家违约), 回滚 PENDING_PAYMENT; 否则 PAID
            // 但没有记录怎么知道原因?
            // 默认策略：如果订单已支付(查支付记录?)
            // 这里抛出异常更安全
            throw new IllegalStateException("未找到违约记录，无法自动判定回滚状态。请人工修正数据。");
        }

        String reason = record.getReason();
        Long userId = record.getUserId();
        int creditDelta = record.getCreditScoreDelta() == null ? 0 : record.getCreditScoreDelta(); // 负数

        // 回滚信用分
        if (creditDelta < 0 && userId != null) {
            User u = userMapper.selectById(userId);
            if (u != null) {
                int current = u.getCreditScore() == null ? 0 : u.getCreditScore();
                u.setCreditScore(current - creditDelta); // - (-10) = +10
                userMapper.updateById(u);
            }
        }

        // 回滚订单状态
        if ("逾期未支付".equals(reason) || "Payment overdue".equals(reason)) {
            // 买家未支付，回滚到 PENDING_PAYMENT
            order.setStatus("PENDING_PAYMENT");
            // 可能还需要恢复保证金? 如果 forfeit 了
            // 但 forfeit 逻辑复杂，暂不支持自动恢复保证金
        } else if ("卖家逾期未发货".equals(reason) || "Shipping overdue".equals(reason)) {
            // 卖家未发货，回滚到 PAID
            order.setStatus("PAID");
        } else {
            // 其他未知原因，默认回滚 PAID? 或者抛异常
            // 假设默认 PAID，因为如果是 BREACH 状态通常是在交易流程中中断的
            log.warn("Unknown breach reason: {}, defaulting status rollback to PAID", reason);
            order.setStatus("PAID");
        }
        orderMapper.updateById(order);

        // 删除违约记录
        breachRecordMapper.deleteById(record.getId());

        // 通知
        notificationService.notifyUser(userId, "Breach record for order " + orderId + " has been revoked by admin.");
    }

    // helper: safe way to find deposit id for item+user; returns null if not found
    private Long depositIdFor(Long userId, Long itemId) {
        try {
            Deposit d = depositService.listByUser(userId).stream()
                    .filter(x -> x.getItemId() != null && x.getItemId().equals(itemId))
                    .findFirst().orElse(null);
            return d == null ? null : d.getId();
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateCreditDeduction(Long userId) {
        // TODO: implement custom logic if needed
        return paymentCreditDeduction;
    }
}
