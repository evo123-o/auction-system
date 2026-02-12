package org.example.auction.schedule;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.entity.Deposit;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.mapper.DepositMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.NotificationService;
import org.example.auction.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 扫描结束拍品、生成订单、冻结中标者保证金、退款非中标者；
 * 无人出价时也退还已缴保证金的用户，通知双方、生成 HTML 凭证。
 */
@Component
public class EndAuctionScheduler {

    private final ItemMapper itemMapper;
    private final BidMapper bidMapper;
    private final DepositMapper depositMapper;
    private final OrderService orderService;
    private final DepositService depositService;
    private final NotificationService notificationService;

    public EndAuctionScheduler(ItemMapper itemMapper,
                               BidMapper bidMapper,
                               DepositMapper depositMapper,
                               OrderService orderService,
                               DepositService depositService,
                               NotificationService notificationService) {
        this.itemMapper = itemMapper;
        this.bidMapper = bidMapper;
        this.depositMapper = depositMapper;
        this.orderService = orderService;
        this.depositService = depositService;
        this.notificationService = notificationService;
    }

    /**
     * 每分钟扫描已到期的拍品（RUNNING 且 end_time <= now）：
     * - 有中标者：生成订单、冻结中标者保证金、为非中标者退款、生成凭证、通知双方、标记 SOLD
     * - 无人出价：标记 CLOSED，退还所有对该拍品已缴保证金（PAID）的用户，并通知卖家
     */
    @Scheduled(cron = "0 * * * * *") // 每分钟
    public void scanEndedItems() {
        List<Item> ended = itemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Item>()
                        .eq(Item::getStatus, "RUNNING")
                        .le(Item::getEndTime, java.time.LocalDateTime.now())
        );

        for (Item item : ended) {
            Bid winner = bidMapper.findTopByItem(item.getId());

            if (winner == null) {
                // 无人出价 -> 关闭并退还所有已缴保证金（状态为 PAID）
                List<Deposit> paidDeposits = depositMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Deposit>()
                                .eq(Deposit::getItemId, item.getId())
                                .eq(Deposit::getStatus, "PAID")
                );
                for (Deposit d : paidDeposits) {
                    try {
                        depositService.refund(d.getUserId(), item.getId());
                        notificationService.notifyUser(d.getUserId(),
                                "Item " + item.getId() + " ended with no bids, your deposit has been refunded.");
                    } catch (Exception ignored) {

                    }
                }

                item.setStatus("CLOSED");
                itemMapper.updateById(item);
                notificationService.notifyUser(item.getCreatedBy(),
                        "Item " + item.getId() + " closed with no bids. All deposits refunded.");
                continue;
            }

            // 有中标者：生成订单
            Order order = orderService.createOrderFromWinningBid(item, winner);

            // 冻结中标者保证金
            depositService.freeze(winner.getUserId(), item.getId());

            // 退款给非中标者（去重后排除中标者）
            List<Bid> allBids = bidMapper.listByItem(item.getId());
            Set<Long> allBidders = allBids.stream()
                    .map(Bid::getUserId)
                    .collect(Collectors.toSet());
            for (Long bidderId : allBidders) {
                if (!bidderId.equals(winner.getUserId())) {
                    try {
                        depositService.refund(bidderId, item.getId());
                        notificationService.notifyUser(bidderId,
                                "You did not win item " + item.getId() + ", your deposit has been refunded.");
                    } catch (Exception ignored) {
                    }
                }
            }

            // 生成 HTML 凭证
            String receiptPath = orderService.generateReceiptHtml(order);
            if (receiptPath != null) {
                order.setReceiptPath(receiptPath);
            }

            // 标记拍品为 SOLD
            item.setStatus("SOLD");
            itemMapper.updateById(item);

            // 通知双方
            notificationService.notifyUser(item.getCreatedBy(),
                    "Your item " + item.getId() + " sold. Order " + order.getId());
            notificationService.notifyUser(winner.getUserId(),
                    "You won item " + item.getId() + ". Order " + order.getId());
        }
    }
}