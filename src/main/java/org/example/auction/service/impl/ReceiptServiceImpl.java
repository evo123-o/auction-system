package org.example.auction.service.impl;

import org.example.auction.service.ReceiptService;
import org.springframework.stereotype.Service;

/**
 * 订单凭证服务实现
 */
@Service
public class ReceiptServiceImpl implements ReceiptService {

    @Override
    public byte[] generatePdf(Long orderId) {
        // TODO: Implement PDF generation
        return new byte[0];
    }
}
