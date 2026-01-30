package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.BidService;
import org.example.auction.service.DepositService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BidServiceImpl implements BidService {

    private final BidMapper bidMapper;
    private final ItemMapper itemMapper;
    private final DepositService depositService;

    @Value("${app.auction.default-extend-minutes:5}")
    private int extendMinutes;

    @Value("${app.auction.max-extend-count:3}")
    private int maxExtendCount;

    @Value("${app.auction.extend-threshold-minutes:5}")
    private int extendThresholdMinutes;

    public BidServiceImpl(BidMapper bidMapper, ItemMapper itemMapper, DepositService depositService) {
        this.bidMapper = bidMapper;
        this.itemMapper = itemMapper;
        this.depositService = depositService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Bid placeBid(Long userId, Long itemId, BigDecimal amount) {
        if (userId == null || itemId == null || amount == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (amount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new IllegalArgumentException("出价金额必须大于 0");
        }

        Item item = itemMapper.selectById(itemId);
        if (item == null) throw new IllegalArgumentException("拍品不存在");

        // 基于时间窗口判断
        LocalDateTime now = LocalDateTime.now();
        if (item.getStartTime() != null && now.isBefore(item.getStartTime())) {
            throw new IllegalArgumentException("auction has not started");
        }
        if (item.getEndTime() != null && now.isAfter(item.getEndTime())) {
            throw new IllegalArgumentException("auction has ended");
        }
        // 若你必须要求 RUNNING 状态，可保留此断言；否则注释以允许时间驱动
        if (!"RUNNING".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalArgumentException("item is not open for bidding");
        }

        // 保证金资格校验
        BigDecimal required = item.getDepositAmount() == null ? BigDecimal.ZERO : item.getDepositAmount();
        boolean eligible = depositService.isEligibleForBidding(userId, itemId, required);
        if (!eligible) {
            throw new IllegalArgumentException("未缴纳保证金或不满足出价条件");
        }

        // 金额必须大于当前价（若当前价为空则取起拍价）
        BigDecimal current = item.getCurrentPrice() == null ? item.getStartPrice() : item.getCurrentPrice();
        if (current == null) current = BigDecimal.ZERO;
        if (amount.compareTo(current) <= 0) {
            throw new IllegalArgumentException("出价必须高于当前价");
        }

        // 记录出价
        Bid bid = new Bid();
        bid.setItemId(itemId);
        bid.setUserId(userId);
        bid.setAmount(amount);
        bid.setBid_time(LocalDateTime.now());
        bidMapper.insert(bid);

        // 更新拍品当前价
        item.setCurrentPrice(amount);

        // 自动延时逻辑：在竞拍结束前N分钟内有新出价时，自动延长竞拍时间
        if (item.getEndTime() != null) {
            LocalDateTime thresholdTime = item.getEndTime().minusMinutes(extendThresholdMinutes);
            int currentExtendCount = item.getExtendCount() == null ? 0 : item.getExtendCount();
            int maxExtend = item.getMaxExtend() == null ? maxExtendCount : item.getMaxExtend();

            // 如果当前时间在结束时间前N分钟内，且还有延时次数
            if (now.isAfter(thresholdTime) && currentExtendCount < maxExtend) {
                // 延长结束时间
                item.setEndTime(item.getEndTime().plusMinutes(extendMinutes));
                item.setExtendCount(currentExtendCount + 1);
            }
        }

        item.setUpdatedAt(LocalDateTime.now());
        itemMapper.updateById(item);

        return bid;
    }

    @Override
    public List<Bid> listByItem(Long itemId) {
        return bidMapper.selectList(
                new LambdaQueryWrapper<Bid>()
                        .eq(Bid::getItemId, itemId)
                        .orderByDesc(Bid::getAmount)
                        .orderByDesc(Bid::getBid_time)
        );
    }
}