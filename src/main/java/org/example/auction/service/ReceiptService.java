package org.example.auction.service;

/**
 * 订单凭证服务
 */
public interface ReceiptService {
    /**
     * 生成订单凭证 PDF
     * @param orderId 订单ID
     * @return PDF字节数组
     */
    byte[] generatePdf(Long orderId);
}
