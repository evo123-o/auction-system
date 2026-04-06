package org.example.auction.schedule;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.DepositMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.NotificationService;
import org.example.auction.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
                        notificationService.notifyUser(
                                d.getUserId(),
                                "SYSTEM",
                                "退还保证金通知",
                                String.format("您参与的拍卖品 '%s'(ID:%d) 因无人出价流拍，您的保证金已退还。", item.getTitle(), item.getId())
                        );
                    } catch (Exception ignored) {

                    }
                }

                item.setStatus("CLOSED");
                itemMapper.updateById(item);
                notificationService.notifyUser(
                        item.getCreatedBy(),
                        "SYSTEM",
                        "流拍通知",
                        String.format("您发布的拍卖品 '%s'(ID:%d) 已结束，但无人参与出价，该商品已流拍。", item.getTitle(), item.getId())
                );
                continue;
            }

            // 有中标者：生成订单
            Order order = orderService.createOrderFromWinningBid(item, winner);

            // 冻结中标者保证金（如果需要）
            if (item.getDepositAmount() != null && item.getDepositAmount().compareTo(java.math.BigDecimal.ZERO) > 0) {
                try {
                    depositService.freeze(winner.getUserId(), item.getId());
                } catch (Exception e) {
                    // 日志记录，防止炸掉整个任务
                    System.err.println("Failed to freeze deposit for user " + winner.getUserId() + " item " + item.getId() + ": " + e.getMessage());
                }
            }

            // 退款给非中标者（去重后排除中标者）
            List<Bid> allBids = bidMapper.listByItem(item.getId());
            Set<Long> allBidders = allBids.stream()
                    .map(Bid::getUserId)
                    .collect(Collectors.toSet());
            for (Long bidderId : allBidders) {
                if (!bidderId.equals(winner.getUserId())) {
                    try {
                        depositService.refund(bidderId, item.getId());
                        notificationService.notifyUser(
                                bidderId,
                                "SYSTEM",
                                "拍卖结束及退还保证金通知",
                                String.format("很遗憾，您未能竞得商品 '%s'(ID:%d)，您的竞拍保证金已解冻并退还至您的账户。", item.getTitle(), item.getId())
                        );
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
            notificationService.notifyUser(
                    item.getCreatedBy(),
                    "ALERT",
                    "恭喜！您的拍卖品已成功售出",
                    String.format("您发布的拍卖品 '%s'(ID:%d) 已经结束并由用户竞拍成功！订单(ID:%d)已经生成，对方最终出价为 ￥%s。",
                            item.getTitle(), item.getId(), order.getId(), winner.getAmount())
            );
            notificationService.notifyUser(
                    winner.getUserId(),
                    "ALERT",
                    "恭喜您赢得拍卖！",
                    String.format("恭喜！您成功竞得了商品 '%s'(ID:%d)！成交价为 ￥%s，订单(ID:%d)已生成。请您尽快前往系统付款中心完成支付操作。",
                            item.getTitle(), item.getId(), winner.getAmount(), order.getId())
            );
        }
    }
}