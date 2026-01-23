package org.example.auction.service.impl;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.BidService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Bid 服务实现：通过 SELECT ... FOR UPDATE 实现数据库级并发控制（行锁）。
 * 使用 DB。
 */
@Service
public class BidServiceImpl implements BidService {

    private final ItemMapper itemMapper;
    private final BidMapper bidMapper;

    public BidServiceImpl(ItemMapper itemMapper, BidMapper bidMapper) {
        this.itemMapper = itemMapper;
        this.bidMapper = bidMapper;
    }

    /**
     * 用事务包裹，隔离级别保持默认（或 READ_COMMITTED），并在事务内对 item 执行 FOR UPDATE。

     * 注意：并发安全依赖数据库的行锁（SELECT ... FOR UPDATE）和事务隔离。
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Bid placeBid(Long userId, Long itemId, BigDecimal amount) {
        Assert.notNull(userId, "userId is required");
        Assert.notNull(itemId, "itemId is required");
        Assert.notNull(amount, "amount is required");

        // 1. lock item row
        Item item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new IllegalArgumentException("item not found: " + itemId);
        }

        // 2. business rule checks
        // require item in RUNNING status (adjust according to your domain)
        if (!"RUNNING".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalArgumentException("item is not open for bidding");
        }

        // cannot bid on own item
        if (item.getCreatedBy() != null && item.getCreatedBy().equals(userId)) {
            throw new IllegalArgumentException("cannot bid on your own item");
        }

        BigDecimal current = item.getCurrentPrice() == null ? item.getStartPrice() : item.getCurrentPrice();
        if (current == null) current = BigDecimal.ZERO;

        if (amount.compareTo(current) <= 0) {
            throw new IllegalArgumentException("bid must be greater than current price");
        }

        // 3. insert bid
        Bid bid = Bid.builder()
                .itemId(itemId)
                .userId(userId)
                .amount(amount)
                .createdAt(LocalDateTime.now())
                .build();
        bidMapper.insert(bid);

        // 4. update item current price
        item.setCurrentPrice(amount);
        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        // 5. transaction commits on method return
        return bid;
    }
}
