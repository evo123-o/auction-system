package org.example.auction.service;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;

public interface OrderService {
    Order getById(Long id);
    /**
     * 根据中标信息生成订单（设置最晚支付时间，例如24小时）
     */
    Order createOrderFromWinningBid(Item item, Bid winnerBid);

    /**
     * 标记订单支付完成
     */
    Order markPaid(Long orderId);

    /**
     * 生成 HTML 凭证，并返回文件路径
     */
    String generateReceiptHtml(Order order);
}
