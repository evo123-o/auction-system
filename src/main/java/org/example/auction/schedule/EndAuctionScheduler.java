package org.example.auction.schedule;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.mapper.BidMapper;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.NotificationService;
import org.example.auction.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 扫描结束拍品、生成订单、冻结保证金、通知、生成 HTML 凭证
 * 以及扫描逾期未支付订单进行违约处理（另见 OverdueOrderScheduler）
 */
@Component
public class EndAuctionScheduler {

    private final ItemMapper itemMapper;
    private final BidMapper bidMapper;
    private final OrderService orderService;
    private final DepositService depositService;
    private final NotificationService notificationService;

    public EndAuctionScheduler(ItemMapper itemMapper,
                               BidMapper bidMapper,
                               OrderService orderService,
                               DepositService depositService,
                               NotificationService notificationService) {
        this.itemMapper = itemMapper;
        this.bidMapper = bidMapper;
        this.orderService = orderService;
        this.depositService = depositService;
        this.notificationService = notificationService;
    }

    /**
     * 每分钟扫描已到期的拍品（RUNNING 且 end_time <= now），生成订单与凭证，冻结中标者保证金，退款其他人（可在后续扩展）。
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
                // 无人出价 -> 关闭
                item.setStatus("CLOSED");
                itemMapper.updateById(item);
                notificationService.notifyUser(item.getCreatedBy(), "Item " + item.getId() + " closed with no bids.");
                continue;
            }
            // 生成订单
            Order order = orderService.createOrderFromWinningBid(item, winner);
            // 冻结保证金（中标者）
            depositService.freeze(winner.getUserId(), item.getId());
            // 生成 HTML 凭证
            String receiptPath = orderService.generateReceiptHtml(order);
            if (receiptPath != null) {
                order.setReceiptPath(receiptPath);
                // 更新订单记录
                itemMapper.updateById(item); // 先确保 item 状态更新为 SOLD
            }
            // 标记拍品为 SOLD
            item.setStatus("SOLD");
            itemMapper.updateById(item);

            // 通知双方
            notificationService.notifyUser(item.getCreatedBy(), "Your item " + item.getId() + " sold. Order " + order.getId());
            notificationService.notifyUser(winner.getUserId(), "You won item " + item.getId() + ". Order " + order.getId());
        }
    }
}
