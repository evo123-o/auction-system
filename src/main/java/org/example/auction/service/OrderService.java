package org.example.auction.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;

public interface OrderService {
    Order getById(Long id);

    /**
     * 根据中标信息生成订单（设置最晚支付时间）
     */
    Order createOrderFromWinningBid(Item item, Bid winnerBid);

    /**
     * 标记订单支付完成
     */
    Order markPaid(Long orderId);

    /**
     * 生成 HTML 凭证内容
     */
    String generateReceiptHtml(Order order);

    /**
     * 分页查询用户的订单（买家视角）
     */
    IPage<Order> pageByBuyer(Page<Order> page, Long buyerId, String status);

    /**
     * 分页查询用户的订单（卖家视角）
     */
    IPage<Order> pageBySeller(Page<Order> page, Long sellerId, String status);

    /**
     * 管理员分页查询所有订单
     */
    IPage<Order> pageAll(Page<Order> page, String status);

    /**
     * 标记订单为已发货
     */
    Order markShipped(Long orderId);

    /**
     * 标记订单为已收货
     */
    Order markReceived(Long orderId);
}
