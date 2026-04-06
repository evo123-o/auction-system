package org.example.auction.service.impl;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

import org.example.auction.entity.Bid;
import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.entity.User;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.mapper.OrderMapper;
import org.example.auction.mapper.UserMapper;
import org.example.auction.service.DepositService;
import org.example.auction.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.Getter;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ItemMapper itemMapper;
    private final UserMapper userMapper;
    @Getter
    private final DepositService depositService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    @Value("${app.pay.deadline-hours:24}")
    private int payDeadlineHours;

    public OrderServiceImpl(OrderMapper orderMapper, ItemMapper itemMapper, UserMapper userMapper, DepositService depositService) {
        this.depositService = depositService;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
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

        Item item = order.getItemId() == null ? null : itemMapper.selectById(order.getItemId());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String createdAt = formatDateTime(order.getCreatedAt(), fmt);
        String payBy = formatDateTime(order.getPayBy(), fmt);
        String buyerName = order.getBuyerName() != null ? order.getBuyerName() : ("用户#" + (order.getBuyerId() != null ? order.getBuyerId() : ""));
        String sellerName = order.getSellerName() != null ? order.getSellerName() : ("用户#" + (order.getSellerId() != null ? order.getSellerId() : ""));
        String finalPrice = formatAmount(order.getFinalPrice());
        String status = order.getStatus() != null ? order.getStatus() : "";
        String itemTitle = item != null && item.getTitle() != null ? item.getTitle() : (order.getItemId() != null ? "拍品 #" + order.getItemId() : "未知拍品");
        String itemCategory = item != null && item.getCategory() != null ? item.getCategory() : "-";
        String itemStatus = item != null && item.getStatus() != null ? item.getStatus() : "-";
        String itemDescription = item != null && item.getDescription() != null && !item.getDescription().isBlank() ? item.getDescription() : "暂无详细描述";
        String itemImageSrc = buildImageSource(item == null ? null : item.getImagePath());
        String itemStartPrice = item != null ? formatAmount(item.getStartPrice()) : "￥0.00";
        String itemCurrentPrice = item != null ? formatAmount(item.getCurrentPrice()) : "￥0.00";
        String itemDeposit = item != null ? formatAmount(item.getDepositAmount()) : "￥0.00";
        String itemStartTime = item != null ? formatDateTime(item.getStartTime(), fmt) : "-";
        String itemEndTime = item != null ? formatDateTime(item.getEndTime(), fmt) : "-";
        String receiptNo = order.getId() == null ? "" : String.format("RCPT-%08d", order.getId());

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>交易凭证</title>");
        html.append("<style data-receipt-theme=\"commercial\">")
                .append("body{margin:0;background:#eef2ff;color:#0f172a;font-family:'Segoe UI','PingFang SC','Microsoft YaHei',Arial,sans-serif;}")
                .append(".paper{max-width:920px;margin:28px auto;background:#fff;border:1px solid #dbe4ff;border-radius:20px;box-shadow:0 18px 44px rgba(15,23,42,.10);overflow:hidden;}")
                .append(".hero{padding:28px 30px 24px;background:linear-gradient(135deg,#0f172a,#1d4ed8);color:#fff;display:flex;justify-content:space-between;gap:20px;align-items:flex-start;}")
                .append(".hero h1{margin:0;font-size:30px;letter-spacing:1px;}")
                .append(".hero p{margin:10px 0 0;opacity:.88;line-height:1.7;font-size:14px;}")
                .append(".badge{display:inline-flex;align-items:center;gap:8px;padding:8px 14px;border-radius:999px;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.24);font-size:13px;font-weight:700;}")
                .append(".content{padding:28px 30px 30px;}")
                .append(".section{margin-top:24px;}")
                .append(".section:first-child{margin-top:0;}")
                .append(".section-title{margin:0 0 14px;font-size:18px;color:#111827;display:flex;align-items:center;gap:10px;}")
                .append(".section-title:before{content:'';display:inline-block;width:4px;height:18px;border-radius:999px;background:#2563eb;}")
                .append(".summary-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;}")
                .append(".summary-card{border:1px solid #e5e7eb;border-radius:16px;background:linear-gradient(180deg,#fff,#f8fafc);padding:16px 16px 14px;min-height:92px;}")
                .append(".summary-label{font-size:13px;color:#6b7280;margin-bottom:10px;}")
                .append(".summary-value{font-size:20px;font-weight:800;color:#0f172a;line-height:1.3;word-break:break-word;}")
                .append(".summary-sub{margin-top:8px;font-size:12px;color:#64748b;}")
                .append(".item-panel{display:grid;grid-template-columns:280px minmax(0,1fr);gap:20px;align-items:start;}")
                .append(".item-media{border:1px solid #e5e7eb;border-radius:18px;overflow:hidden;background:#f8fafc;min-height:260px;display:flex;align-items:center;justify-content:center;}")
                .append(".item-media img{display:block;width:100%;height:100%;max-height:320px;object-fit:cover;}")
                .append(".item-fallback{padding:32px 20px;text-align:center;color:#64748b;line-height:1.7;}")
                .append(".item-title{margin:0 0 10px;font-size:24px;line-height:1.35;color:#0f172a;}")
                .append(".item-meta{margin:0 0 16px;color:#475569;font-size:14px;line-height:1.8;}")
                .append(".tag-row{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:18px;}")
                .append(".tag{display:inline-flex;align-items:center;padding:6px 12px;border-radius:999px;background:#eff6ff;color:#1d4ed8;font-size:12px;font-weight:700;}")
                .append(".tag.neutral{background:#f1f5f9;color:#334155;}")
                .append(".detail-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 16px;}")
                .append(".detail-item{padding:14px 16px;border:1px solid #e5e7eb;border-radius:14px;background:#fff;}")
                .append(".detail-label{font-size:12px;color:#6b7280;margin-bottom:8px;text-transform:uppercase;letter-spacing:.04em;}")
                .append(".detail-value{font-size:15px;line-height:1.65;color:#111827;word-break:break-word;}")
                .append(".description-box{padding:16px 18px;border:1px solid #dbeafe;border-radius:16px;background:#f8fbff;color:#1e293b;line-height:1.8;white-space:pre-wrap;}")
                .append(".note-box{margin-top:26px;padding:14px 18px;border-radius:14px;background:#f8fafc;border:1px solid #e2e8f0;color:#475569;line-height:1.7;font-size:13px;}")
                .append(".footer{padding:0 30px 30px;color:#94a3b8;font-size:12px;text-align:right;}")
                .append("@media (max-width: 860px){.hero,.content,.footer{padding-left:18px;padding-right:18px}.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr));}.item-panel{grid-template-columns:1fr;}.item-media{min-height:220px;}.detail-grid{grid-template-columns:1fr;}.hero{flex-direction:column;}.hero h1{font-size:26px;}}")
                .append("@media print{body{background:#fff}.paper{margin:0;border:0;border-radius:0;box-shadow:none;}.hero{border-radius:0;}.content{padding-top:22px;}}");
        html.append("</style></head><body><div class='paper'>");
        html.append("<div class='hero'><div><h1>交易凭证</h1><p>订单完成后自动生成的交易凭据，包含买卖双方、拍品信息、成交价格与支付时限，便于核对与留档。</p></div><div class='badge'>").append(escapeHtml(receiptNo)).append("</div></div>");
        html.append("<div class='content'>");

        html.append("<div class='section'><h2 class='section-title'>订单摘要</h2><div class='summary-grid'>");
        appendSummaryCard(html, "订单编号", escapeHtml(receiptNo.isBlank() ? String.valueOf(order.getId() == null ? "" : order.getId()) : receiptNo), "交易凭证编号");
        appendSummaryCard(html, "成交价格", escapeHtml(finalPrice), "最终成交金额");
        appendSummaryCard(html, "订单状态", escapeHtml(status), "当前交易状态");
        appendSummaryCard(html, "支付截止", escapeHtml(payBy.isBlank() ? "-" : payBy), "请在截止时间前完成支付");
        html.append("</div></div>");

        html.append("<div class='section'><h2 class='section-title'>拍品信息</h2><div class='item-panel'>");
        html.append("<div class='item-media'>");
        if (itemImageSrc != null) {
            html.append("<img src=\"").append(escapeHtml(itemImageSrc)).append("\" alt=\"").append(escapeHtml(itemTitle)).append("\">");
        } else {
            html.append("<div class='item-fallback'>暂无商品图片<br>系统未检测到可展示的拍品图</div>");
        }
        html.append("</div>");
        html.append("<div>");
        html.append("<h3 class='item-title'>").append(escapeHtml(itemTitle)).append("</h3>");
        html.append("<p class='item-meta'>商品分类：").append(escapeHtml(itemCategory)).append("<br>拍品编号：").append(order.getItemId() == null ? "-" : escapeHtml(String.valueOf(order.getItemId()))).append("<br>拍卖状态：").append(escapeHtml(itemStatus)).append("</p>");
        html.append("<div class='tag-row'>");
        html.append("<span class='tag'>起拍价 ").append(escapeHtml(itemStartPrice)).append("</span>");
        html.append("<span class='tag neutral'>当前价 ").append(escapeHtml(itemCurrentPrice)).append("</span>");
        html.append("<span class='tag neutral'>保证金 ").append(escapeHtml(itemDeposit)).append("</span>");
        html.append("</div>");
        html.append("<div class='detail-grid'>");
        appendDetailItem(html, "拍卖开始时间", itemStartTime.isBlank() ? "-" : escapeHtml(itemStartTime));
        appendDetailItem(html, "拍卖结束时间", itemEndTime.isBlank() ? "-" : escapeHtml(itemEndTime));
        appendDetailItem(html, "卖家", escapeHtml(sellerName));
        appendDetailItem(html, "买家", escapeHtml(buyerName));
        html.append("</div>");
        html.append("</div></div>");

        html.append("<div class='section'><h2 class='section-title'>交易详情</h2>");
        html.append("<div class='detail-grid'>");
        appendDetailItem(html, "买家 ID", order.getBuyerId() == null ? "-" : escapeHtml(String.valueOf(order.getBuyerId())));
        appendDetailItem(html, "卖家 ID", order.getSellerId() == null ? "-" : escapeHtml(String.valueOf(order.getSellerId())));
        appendDetailItem(html, "下单时间", createdAt.isBlank() ? "-" : escapeHtml(createdAt));
        appendDetailItem(html, "支付截止", payBy.isBlank() ? "-" : escapeHtml(payBy));
        appendDetailItem(html, "订单状态", escapeHtml(status));
        appendDetailItem(html, "成交金额", escapeHtml(finalPrice));
        html.append("</div></div>");

        html.append("<div class='section'><h2 class='section-title'>拍品描述</h2><div class='description-box'>").append(escapeHtml(itemDescription)).append("</div></div>");

        html.append("<div class='note-box'>提示：本凭证可用于订单留存与交易核验。若拍品图片无法显示，请检查商品上传文件是否可访问。</div>");
        html.append("</div><div class='footer'>系统自动生成时间：").append(escapeHtml(LocalDateTime.now().format(fmt))).append("</div></div></body></html>");

        return html.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "￥0.00";
        }
        try {
            return "￥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (ArithmeticException ex) {
            return "￥" + amount.toPlainString();
        }
    }

    private String formatDateTime(LocalDateTime dateTime, DateTimeFormatter formatter) {
        return dateTime == null ? "" : dateTime.format(formatter);
    }

    private String buildImageSource(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        byte[] imageBytes = readImageBytes(imagePath);
        if (imageBytes != null && imageBytes.length > 0) {
            return "data:" + resolveMimeType(imagePath) + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
        }

        if (imagePath.startsWith("http://") || imagePath.startsWith("https://")) {
            return imagePath;
        }

        if (imagePath.startsWith("/")) {
            return imagePath;
        }

        return uploadBaseUrl + "/" + imagePath.replace('\\', '/');
    }

    private byte[] readImageBytes(String imagePath) {
        try {
            Path filePath = resolveImageFilePath(imagePath);
            if (filePath == null || !Files.exists(filePath)) {
                return null;
            }
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            return null;
        }
    }

    private Path resolveImageFilePath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return null;
        }

        String relativePath = imagePath;
        if (relativePath.startsWith(uploadBaseUrl)) {
            relativePath = relativePath.substring(uploadBaseUrl.length());
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        Path basePath = Paths.get(uploadDir);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath).toAbsolutePath();
        }

        return basePath.resolve(relativePath).normalize();
    }

    private String resolveMimeType(String imagePath) {
        String extension = "";
        int dotIndex = imagePath.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < imagePath.length() - 1) {
            extension = imagePath.substring(dotIndex + 1).toLowerCase();
        }

        switch (extension) {
            case "png" -> {
                return "image/png";
            }
            case "gif" -> {
                return "image/gif";
            }
            case "webp" -> {
                return "image/webp";
            }
            case "avif" -> {
                return "image/avif";
            }
        }

        String mimeType = URLConnection.guessContentTypeFromName(imagePath);
        return mimeType != null ? mimeType : "image/jpeg";
    }

    private void appendSummaryCard(StringBuilder html, String label, String value, String hint) {
        html.append("<div class='summary-card'><div class='summary-label'>").append(escapeHtml(label))
                .append("</div><div class='summary-value'>").append(value)
                .append("</div><div class='summary-sub'>").append(escapeHtml(hint)).append("</div></div>");
    }

    private void appendDetailItem(StringBuilder html, String label, String value) {
        html.append("<div class='detail-item'><div class='detail-label'>")
                .append(escapeHtml(label))
                .append("</div><div class='detail-value'>")
                .append(value == null || value.isBlank() ? "-" : value)
                .append("</div></div>");
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
