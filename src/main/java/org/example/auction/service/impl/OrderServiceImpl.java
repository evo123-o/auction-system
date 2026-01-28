package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Getter;
import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    @Getter
    private final DepositService depositService;

    @Value("${app.receipts.dir:receipts}")
    private String receiptsDir;

    @Value("${app.pay.deadline-hours:24}")
    private int payDeadlineHours;

    public OrderServiceImpl(OrderMapper orderMapper, DepositService depositService) {
        this.depositService = depositService;
        this.orderMapper = orderMapper;
    }


    @Override
    public Order getById(Long id) {
        return orderMapper.selectById(id);
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
        return order;
    }

    @Override
    public String generateReceiptHtml(Order order) {
        try {
            File dir = new File(receiptsDir);
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("无法创建目录: " + receiptsDir);
            }
            String filename = "order-" + order.getId() + ".html";
            File f = new File(dir, filename);
            try (FileWriter w = new FileWriter(f)) {
                w.write("<!DOCTYPE html><html><head><meta charset='utf-8'><title>Order Receipt</title></head><body>");
                w.write("<h2>Order Receipt</h2>");
                w.write("<p>Order ID: " + order.getId() + "</p>");
                w.write("<p>Item ID: " + order.getItemId() + "</p>");
                w.write("<p>Seller ID: " + order.getSellerId() + "</p>");
                w.write("<p>Buyer ID: " + order.getBuyerId() + "</p>");
                w.write("<p>Final Price: " + order.getFinalPrice() + "</p>");
                w.write("<p>Status: " + order.getStatus() + "</p>");
                w.write("<p>Created At: " + order.getCreatedAt() + "</p>");
                w.write("</body></html>");
            }
            return f.getAbsolutePath();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public IPage<Order> pageByBuyer(Page<Order> page, Long buyerId, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getBuyerId, buyerId)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(page, qw);
    }

    @Override
    public IPage<Order> pageBySeller(Page<Order> page, Long sellerId, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(Order::getSellerId, sellerId)
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(page, qw);
    }

    @Override
    public IPage<Order> pageAll(Page<Order> page, String status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<Order>()
                .eq(status != null && !status.isBlank(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        return orderMapper.selectPage(page, qw);
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
        return order;
    }

}
