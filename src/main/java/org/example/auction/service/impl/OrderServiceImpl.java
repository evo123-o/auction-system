package org.example.auction.service.impl;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.mapper.OrderMapper;
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

    @Value("${app.receipts.dir:receipts}")
    private String receiptsDir;

    @Value("${app.pay.deadline-hours:24}")
    private int payDeadlineHours;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
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
    @Transactional
    public Order markPaid(Long orderId) {
        Order o = orderMapper.selectById(orderId);
        if (o == null) throw new IllegalArgumentException("order not found: " + orderId);
        o.setStatus("PAID");
        orderMapper.updateById(o);
        return o;
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
}
