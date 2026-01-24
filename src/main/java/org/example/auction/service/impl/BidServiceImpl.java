package org.example.auction.service.impl;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.BidService;
import org.example.auction.service.DepositService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bid 服务实现：通过 SELECT ... FOR UPDATE 实现数据库级并发控制（行锁）。
 * 使用数据库进行并发控制。
 */
@Service
public class BidServiceImpl implements BidService {

    private final ItemMapper itemMapper;
    private final BidMapper bidMapper;
    private final DepositService depositService;

    public BidServiceImpl(ItemMapper itemMapper, BidMapper bidMapper, DepositService depositService) {
        this.itemMapper = itemMapper;
        this.bidMapper = bidMapper;
        this.depositService = depositService;
    }
    /**
     * 在事务中执行，隔离级别保持默认（或 READ_COMMITTED），并在事务内对 item 执行 FOR UPDATE 锁定。
     * 注意：并发安全依赖于数据库的行锁（SELECT ... FOR UPDATE）和事务隔离。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Bid placeBid(Long userId, Long itemId, BigDecimal amount) {
        // 保证金校验
        Item itemSnapshot = itemMapper.selectById(itemId);
        java.math.BigDecimal required = itemSnapshot == null ? java.math.BigDecimal.ZERO : itemSnapshot.getDepositAmount();
        if (!depositService.isEligibleForBidding(userId, itemId, required)) {
            throw new IllegalArgumentException("deposit not paid or insufficient");
        }
        // 1. 锁定 item 所在行
        Item item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new IllegalArgumentException("item not found: " + itemId);
        }

        // 2. 业务规则校验
        // 要求 item 处于 RUNNING 状态（根据业务领域进行调整）
        if (!"RUNNING".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalArgumentException("item is not open for bidding");
        }

        // 不能对自己发布的商品出价
        if (item.getCreatedBy() != null && item.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("cannot bid on your own item");
        }

        BigDecimal current = item.getCurrentPrice() == null ? item.getStartPrice() : item.getCurrentPrice();
        if (current == null) current = BigDecimal.ZERO;

        if (amount.compareTo(current) <= 0) {
            throw new IllegalArgumentException("bid must be greater than current price");
        }

        // 3. 插入出价记录
        Bid bid = Bid.builder()
                .itemId(itemId)
                .userId(userId)
                .amount(amount)
                .bid_time(LocalDateTime.now())
                .build();
        bidMapper.insert(bid);

        // 4. 更新 item 的当前价格
        item.setCurrentPrice(amount);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        // 5. 方法返回时事务提交
        return bid;

    }

}

