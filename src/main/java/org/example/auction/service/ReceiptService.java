package org.example.auction.service;

public interface ReceiptService {
    byte[] generatePdf(Long orderId);
}
