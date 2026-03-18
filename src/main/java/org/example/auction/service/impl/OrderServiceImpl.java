package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final UserMapper userMapper;
    @Getter
    private final DepositService depositService;

    @Value("${app.receipts.dir:receipts}")
    private String receiptsDir;

    @Value("${app.pay.deadline-hours:24}")
    private int payDeadlineHours;

    public OrderServiceImpl(OrderMapper orderMapper, UserMapper userMapper, DepositService depositService) {
        this.depositService = depositService;
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
    }


    @Override
    public Order getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order != null) fillNames(order);
        return order;
    }

    @Override
    @Transactional
    public Order createOrderFromWinningBid(Item item, Bid winnerBid) {
        Order order = Order.builder()
                .itemId(item.getId())
                .sellerId(item.getCreatedBy())
                .buyerId(winnerBid.getUserId())
                .finalPrice(winnerBid.getAmount() == null ? BigDecimal.ZERO : winnerBid.getAmount())
                .status("PENDING_PAYMENT")
                .createdAt(LocalDateTime.now())
                .payBy(LocalDateTime.now().plusHours(payDeadlineHours))
                .build();
        orderMapper.insert(order);
        fillNames(order);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order markPaid(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        // 仅当非 PAID 时更新
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            order.setStatus("PAID");
            order.setPayBy(LocalDateTime.now());
            orderMapper.updateById(order);
        }
        fillNames(order);
        return order;
    }

    @Override
    public String generateReceiptHtml(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("order is null");
        }
        fillNames(order);

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String createdAt = order.getCreatedAt() != null ? order.getCreatedAt().format(fmt) : "";
        String payBy = order.getPayBy() != null ? order.getPayBy().format(fmt) : "";
        String buyerName = order.getBuyerName() != null ? order.getBuyerName() : ("用户#" + (order.getBuyerId() != null ? order.getBuyerId() : ""));
        String sellerName = order.getSellerName() != null ? order.getSellerName() : ("用户#" + (order.getSellerId() != null ? order.getSellerId() : ""));
        String finalPrice = order.getFinalPrice() != null ? order.getFinalPrice().toString() : "0.00";
        String status = order.getStatus() != null ? order.getStatus() : "";

        return "<!doctype html><html><head><meta charset='utf-8'><title>交易凭证</title>" +
                "<style>" +
                "body{font-family:Helvetica,Arial,\"Microsoft YaHei\",sans-serif;color:#222;background:#f3f4f6;padding:20px}" +
                ".paper{max-width:800px;margin:0 auto;background:#fff;padding:24px;border:1px solid #e6e6e6}" +
                ".head{display:flex;justify-content:space-between;align-items:center;border-bottom:2px solid #efefef;padding-bottom:12px;margin-bottom:18px}" +
                ".meta{color:#666;font-size:14px;margin-bottom:12px}" +
                ".row{margin:8px 0}" +
                "</style></head><body>" +
                "<div class='paper'>" +
                "<div class='head'><h2>交易凭证</h2><div class='meta'>订单ID: " + (order.getId() != null ? order.getId() : "") + "</div></div>" +
                "<div class='row'><strong>买家：</strong>" + escapeHtml(buyerName) + "</div>" +
                "<div class='row'><strong>卖家：</strong>" + escapeHtml(sellerName) + "</div>" +
                "<div class='row'><strong>成交价：</strong>" + escapeHtml(finalPrice) + "</div>" +
                "<div class='row'><strong>状态：</strong>" + escapeHtml(status) + "</div>" +
                "<div class='row'><strong>下单时间：</strong>" + escapeHtml(createdAt) + "</div>" +
                "<div class='row'><strong>支付截止：</strong>" + escapeHtml(payBy) + "</div>" +
                "</div></body></html>";
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    @Override
    public IPage<Order> pageByBuyer(Page<Order> page, Long buyerId, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getBuyerId, buyerId)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderMapper.selectPage(page, qw);
        fillNames(result.getRecords());
        return result;
    }

    @Override
    public IPage<Order> pageBySeller(Page<Order> page, Long sellerId, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getSellerId, sellerId)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderMapper.selectPage(page, qw);
        fillNames(result.getRecords());
        return result;
    }

    @Override
    public IPage<Order> pageAll(Page<Order> page, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        IPage<Order> result = orderMapper.selectPage(page, qw);
        fillNames(result.getRecords());
        return result;
    }

    @Override
    @Transactional
    public Order markShipped(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("订单状态不允许发货");
        }
        order.setStatus("SHIPPED");
        orderMapper.updateById(order);
        fillNames(order);
        return order;
    }

    @Override
    @Transactional
    public Order markReceived(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"SHIPPED".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("订单状态不允许确认收货");
        }
        order.setStatus("RECEIVED");
        orderMapper.updateById(order);
        fillNames(order);
        return order;
    }

    private void fillNames(List<Order> orders) {
        if (orders == null || orders.isEmpty()) return;
        for (Order o : orders) {
            fillNames(o);
        }
    }

    private void fillNames(Order order) {
        if (order == null) return;
        if (order.getBuyerId() != null) {
            User buyer = userMapper.selectById(order.getBuyerId());
            if (buyer != null) order.setBuyerName(buyer.getUsername());
        }
        if (order.getSellerId() != null) {
            User seller = userMapper.selectById(order.getSellerId());
            if (seller != null) order.setSellerName(seller.getUsername());
        }
    }
}
