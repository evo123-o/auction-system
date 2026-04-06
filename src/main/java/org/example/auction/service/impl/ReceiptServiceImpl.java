package org.example.auction.service.impl;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.example.auction.entity.Item;
import org.example.auction.entity.Order;
import org.example.auction.mapper.ItemMapper;
import org.example.auction.service.OrderService;
import org.example.auction.service.ReceiptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private final OrderService orderService;
    private final ItemMapper itemMapper;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.base-url:/uploads}")
    private String uploadBaseUrl;

    public ReceiptServiceImpl(OrderService orderService, ItemMapper itemMapper) {
        this.orderService = orderService;
        this.itemMapper = itemMapper;
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

        Item item = order.getItemId() == null ? null : itemMapper.selectById(order.getItemId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);

            document.open();
            BaseFont cnBaseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(cnBaseFont, 18, Font.BOLD, new BaseColor(15, 23, 42));
            Font sectionFont = new Font(cnBaseFont, 14, Font.BOLD, new BaseColor(30, 64, 175));
            Font bodyFont = new Font(cnBaseFont, 11, Font.NORMAL, new BaseColor(17, 24, 39));
            Font subFont = new Font(cnBaseFont, 9, Font.NORMAL, new BaseColor(100, 116, 139));

            Paragraph title = new Paragraph("交易凭证 / Order Receipt", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(spacer(1));

            Paragraph meta = new Paragraph("订单编号：" + receiptNo(order) + "    生成时间：" + LocalDateTime.now().format(formatter), subFont);
            meta.setAlignment(Element.ALIGN_CENTER);
            document.add(meta);
            document.add(spacer(1));

            addSectionHeader(document, "订单摘要", sectionFont);
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingBefore(4f);
            summaryTable.setSpacingAfter(8f);
            summaryTable.setWidths(new float[]{1.1f, 2.5f});
            addKeyValue(summaryTable, "订单编号", receiptNo(order), bodyFont);
            addKeyValue(summaryTable, "成交价格", formatAmount(order.getFinalPrice()), bodyFont);
            addKeyValue(summaryTable, "订单状态", safeText(order.getStatus()), bodyFont);
            addKeyValue(summaryTable, "支付截止", formatDateTime(order.getPayBy(), formatter), bodyFont);
            document.add(summaryTable);

            addSectionHeader(document, "拍品信息", sectionFont);
            if (item != null) {
                PdfPTable itemTable = new PdfPTable(2);
                itemTable.setWidthPercentage(100);
                itemTable.setSpacingBefore(4f);
                itemTable.setSpacingAfter(8f);
                itemTable.setWidths(new float[]{1.2f, 2.8f});

                PdfPCell imageCell = new PdfPCell();
                imageCell.setBorder(Rectangle.BOX);
                imageCell.setPadding(8f);
                imageCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                imageCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                imageCell.setMinimumHeight(180f);

                byte[] imageBytes = readImageBytes(item.getImagePath());
                if (imageBytes != null && imageBytes.length > 0) {
                    try {
                        Image image = Image.getInstance(imageBytes);
                        image.scaleToFit(220f, 160f);
                        image.setAlignment(Element.ALIGN_CENTER);
                        imageCell.addElement(image);
                    } catch (DocumentException | java.io.IOException ex) {
                        imageCell.addElement(new Paragraph("图片暂不支持预览", subFont));
                    }
                } else {
                    imageCell.addElement(new Paragraph("暂无拍品图片", subFont));
                }

                PdfPCell infoCell = new PdfPCell();
                infoCell.setBorder(Rectangle.BOX);
                infoCell.setPadding(10f);
                infoCell.addElement(new Paragraph("拍品名称：" + safeText(item.getTitle()), bodyFont));
                infoCell.addElement(new Paragraph("商品分类：" + safeText(item.getCategory()), bodyFont));
                infoCell.addElement(new Paragraph("拍品编号：" + safeText(order.getItemId()), bodyFont));
                infoCell.addElement(new Paragraph("起拍价：" + formatAmount(item.getStartPrice()), bodyFont));
                infoCell.addElement(new Paragraph("当前价：" + formatAmount(item.getCurrentPrice()), bodyFont));
                infoCell.addElement(new Paragraph("保证金：" + formatAmount(item.getDepositAmount()), bodyFont));
                infoCell.addElement(new Paragraph("拍卖状态：" + safeText(item.getStatus()), bodyFont));
                infoCell.addElement(new Paragraph("拍卖开始：" + formatDateTime(item.getStartTime(), formatter), bodyFont));
                infoCell.addElement(new Paragraph("拍卖结束：" + formatDateTime(item.getEndTime(), formatter), bodyFont));

                itemTable.addCell(imageCell);
                itemTable.addCell(infoCell);
                document.add(itemTable);
            }

            addSectionHeader(document, "交易详情", sectionFont);
            PdfPTable detailTable = new PdfPTable(2);
            detailTable.setWidthPercentage(100);
            detailTable.setSpacingBefore(4f);
            detailTable.setSpacingAfter(8f);
            detailTable.setWidths(new float[]{1.1f, 2.5f});
            addKeyValue(detailTable, "买家", safeText(order.getBuyerName()), bodyFont);
            addKeyValue(detailTable, "卖家", safeText(order.getSellerName()), bodyFont);
            addKeyValue(detailTable, "买家 ID", safeText(order.getBuyerId()), bodyFont);
            addKeyValue(detailTable, "卖家 ID", safeText(order.getSellerId()), bodyFont);
            addKeyValue(detailTable, "下单时间", formatDateTime(order.getCreatedAt(), formatter), bodyFont);
            addKeyValue(detailTable, "成交金额", formatAmount(order.getFinalPrice()), bodyFont);
            document.add(detailTable);

            addSectionHeader(document, "拍品描述", sectionFont);
            Paragraph description = new Paragraph(safeText(item == null ? null : item.getDescription()), bodyFont);
            description.setLeading(18f);
            document.add(description);

            document.add(spacer(2));
            Paragraph note = new Paragraph("提示：本凭证可用于交易留档与信息核验。如图片未正常显示，请确认拍品图片文件是否存在。", subFont);
            note.setSpacingBefore(8f);
            document.add(note);

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException | java.io.IOException ex) {
            throw new IllegalStateException("生成PDF失败", ex);
        } catch (RuntimeException ex) {
            throw new IllegalStateException("生成订单凭证失败", ex);
        }
    }

    private Paragraph spacer(int lines) {
        Paragraph paragraph = new Paragraph(" ");
        paragraph.setSpacingAfter(6f * lines);
        return paragraph;
    }

    private void addSectionHeader(Document document, String title, Font font) throws DocumentException {
        Paragraph sectionTitle = new Paragraph(title, font);
        sectionTitle.setSpacingBefore(6f);
        sectionTitle.setSpacingAfter(6f);
        document.add(sectionTitle);
    }

    private void addKeyValue(PdfPTable table, String key, String value, Font font) {
        PdfPCell keyCell = new PdfPCell(new Phrase(key, font));
        keyCell.setBackgroundColor(new BaseColor(248, 250, 252));
        keyCell.setBorderColor(new BaseColor(229, 231, 235));
        keyCell.setPadding(10f);
        table.addCell(keyCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value == null || value.isBlank() ? "-" : value, font));
        valueCell.setBorderColor(new BaseColor(229, 231, 235));
        valueCell.setPadding(10f);
        table.addCell(valueCell);
    }

    private String receiptNo(Order order) {
        if (order == null || order.getId() == null) {
            return "";
        }
        return String.format("RCPT-%08d", order.getId());
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
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

    private byte[] readImageBytes(String imagePath) {
        try {
            if (StringUtils.isBlank(imagePath)) {
                return null;
            }
            Path filePath = resolveImageFilePath(imagePath);
            if (filePath == null || !Files.exists(filePath)) {
                return null;
            }
            return Files.readAllBytes(filePath);
        } catch (java.io.IOException ex) {
            return null;
        }
    }

    private Path resolveImageFilePath(String imagePath) {
        if (StringUtils.isBlank(imagePath)) {
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
}
