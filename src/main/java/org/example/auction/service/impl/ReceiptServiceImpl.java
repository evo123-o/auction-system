package org.example.auction.service.impl;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import org.example.auction.entity.Order;
import org.example.auction.service.OrderService;
import org.example.auction.service.ReceiptService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final OrderService orderService;

    public ReceiptServiceImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public byte[] generatePdf(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("订单ID不能为空");
        }

        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);

            document.open();
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD);
            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);

            document.add(new Paragraph("Auction Order Receipt", titleFont));
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph("Order ID: " + order.getId(), bodyFont));
            document.add(new Paragraph("Buyer: " + (order.getBuyerName() == null ? "" : order.getBuyerName()), bodyFont));
            document.add(new Paragraph("Seller: " + (order.getSellerName() == null ? "" : order.getSellerName()), bodyFont));
            document.add(new Paragraph("Final Price: " + (order.getFinalPrice() == null ? "0.00" : order.getFinalPrice()), bodyFont));
            document.add(new Paragraph("Status: " + (order.getStatus() == null ? "" : order.getStatus()), bodyFont));
            document.add(new Paragraph("Created At: " + (order.getCreatedAt() == null ? "" : order.getCreatedAt().format(formatter)), bodyFont));
            document.add(new Paragraph("Pay By: " + (order.getPayBy() == null ? "" : order.getPayBy().format(formatter)), bodyFont));

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("生成PDF失败", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("生成订单凭证失败", ex);
        }
    }
}
