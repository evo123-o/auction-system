package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.NonNull;
import org.example.auction.dto.PlaceBidResult;
import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.User;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.mapper.UserMapper;
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
    private final UserMapper userMapper;
    private final DepositService depositService;

    @Value("${app.auction.default-extend-minutes:5}")
    private int extendMinutes;

    @Value("${app.auction.max-extend-count:3}")
    private int maxExtendCount;

    @Value("${app.auction.extend-threshold-minutes:5}")
    private int extendThresholdMinutes;

    @Value("${app.auction.credit-score.min-to-bid:60}")
    private int minCreditScoreToBid;

    public BidServiceImpl(BidMapper bidMapper, ItemMapper itemMapper, UserMapper userMapper, DepositService depositService) {
        this.bidMapper = bidMapper;
        this.itemMapper = itemMapper;
        this.userMapper = userMapper;
        this.depositService = depositService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaceBidResult placeBid(Long userId, Long itemId, BigDecimal amount) {
        if (userId == null || itemId == null || amount == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        // --- 信用分检查 ---
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        int currentScore = user.getCreditScore() != null ? user.getCreditScore() : 100;
        if (currentScore < minCreditScoreToBid) {
            throw new IllegalArgumentException("您的信用分过低 (" + currentScore + " < " + minCreditScoreToBid + ")，无法参与竞拍！");
        }

        if (amount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
            throw new IllegalArgumentException("出价金额必须大于 0");
        }

        // 使用悲观锁 (FOR UPDATE) 获取 Item，防止并发导致的出价覆盖或价格不一致
        Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
                .eq(Item::getId, itemId)
                .last("FOR UPDATE"));

        if (item == null) throw new IllegalArgumentException("拍品不存在");

        // 基于时间窗口判断
        LocalDateTime now = LocalDateTime.now();
        if (item.getStartTime() != null && now.isBefore(item.getStartTime())) {
            throw new IllegalArgumentException("拍卖尚未开始");
        }
        if (item.getEndTime() != null && now.isAfter(item.getEndTime())) {
            throw new IllegalArgumentException("拍卖已结束");
        }

        // 若状态校验不是必需的，可移除下行
        if (!"RUNNING".equalsIgnoreCase(item.getStatus())) {
            throw new IllegalArgumentException("商品未处于竞拍状态");
        }

        // --- 功能 1: 检查是否已经是最高出价者 ---
        // 查询当前商品的最高出价记录 (LIMIT 1)
        Bid highestBid = bidMapper.selectOne(new LambdaQueryWrapper<Bid>()
                .eq(Bid::getItemId, itemId)
                .orderByDesc(Bid::getAmount)
                .last("LIMIT 1"));

        if (highestBid != null) {
            // 如果当前用户已经是最高出价者
            if (highestBid.getUserId().equals(userId)) {
                throw new IllegalArgumentException("您已经是当前最高出价者，无需重复出价！");
            }
            // 确保出价高于最高价
            if (amount.compareTo(highestBid.getAmount()) <= 0) {
                throw new IllegalArgumentException("出价必须高于当前最高价: " + highestBid.getAmount());
            }
        } else {
            // 如果没有任何出价，检查是否高于起拍价
            BigDecimal startPrice = item.getStartPrice() != null ? item.getStartPrice() : BigDecimal.ZERO;
            if (amount.compareTo(startPrice) < 0) {
                throw new IllegalArgumentException("出价不能低于起拍价: " + startPrice);
            }
        }

        // 保证金资格校验
        BigDecimal required = item.getDepositAmount() == null ? BigDecimal.ZERO : item.getDepositAmount();
        boolean eligible = depositService.isEligibleForBidding(userId, itemId, required);
        if (!eligible) {
            throw new IllegalArgumentException("未缴纳保证金或不满足出价条件");
        }

        // 记录出价
        Bid bid = new Bid();
        bid.setItemId(itemId);
        bid.setUserId(userId);
        bid.setAmount(amount);
        bid.setBid_time(LocalDateTime.now());
        bidMapper.insert(bid);

        boolean extended = false;
        LocalDateTime newEnd = null;
        Integer newExtendCount = item.getExtendCount();

        // 使用 LambdaUpdateWrapper 强制更新 Item，确保 SQL 一定执行
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Item> updateWrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(Item::getId, item.getId())
                .set(Item::getCurrentPrice, amount)
                .set(Item::getUpdatedAt, LocalDateTime.now());

        // 功能 2: 自动延时逻辑
        if (Boolean.TRUE.equals(item.getAutoExtension()) && item.getEndTime() != null) {
            LocalDateTime thresholdTime = item.getEndTime().minusMinutes(extendThresholdMinutes);
            int currentExtendCount = item.getExtendCount() == null ? 0 : item.getExtendCount();
            int maxExtend = item.getMaxExtend() == null ? maxExtendCount : item.getMaxExtend();

            // 如果当前时间在结束时间前N分钟内，且还有延时次数
            if (now.isAfter(thresholdTime) && currentExtendCount < maxExtend) {
                // 延长结束时间
                newEnd = item.getEndTime().plusMinutes(extendMinutes);
                updateWrapper.set(Item::getEndTime, newEnd);
                updateWrapper.set(Item::getExtendCount, currentExtendCount + 1);
                newExtendCount = currentExtendCount + 1;
                extended = true;
            }
        }

        itemMapper.update(null, updateWrapper);

        return PlaceBidResult.builder()
                .bid(bid)
                .extended(extended)
                .newEndTime(newEnd)
                .extendCount(newExtendCount)
                .build();
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

    @Override
    public org.springframework.data.domain.Page<@NonNull Bid> pageAll(org.springframework.data.domain.Pageable pageable) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Bid> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageable.getPageNumber() + 1, pageable.getPageSize());

        com.baomidou.mybatisplus.core.metadata.IPage<Bid> result = bidMapper.selectPage(mpPage,
                new LambdaQueryWrapper<Bid>().orderByDesc(Bid::getBid_time));

        return new org.springframework.data.domain.PageImpl<>(result.getRecords(), pageable, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBid(Long bidId) {
        Bid bid = bidMapper.selectById(bidId);
        if (bid == null) throw new IllegalArgumentException("出价记录不存在");

        Long itemId = bid.getItemId();

        // 删除记录
        bidMapper.deleteById(bidId);

        // 重新计算最高价
        Bid highestFormat = bidMapper.selectOne(new LambdaQueryWrapper<Bid>()
                .eq(Bid::getItemId, itemId)
                .orderByDesc(Bid::getAmount)
                .last("LIMIT 1"));

        BigDecimal newPrice;
        if (highestFormat != null) {
            newPrice = highestFormat.getAmount();
        } else {
            // 没有出价了，恢复起拍价
            Item item = itemMapper.selectById(itemId);
            newPrice = item != null ? item.getStartPrice() : BigDecimal.ZERO;
        }

        // 更新 Item
        Item updateItem = new Item();
        updateItem.setId(itemId);
        updateItem.setCurrentPrice(newPrice);
        itemMapper.updateById(updateItem);
    }
}
