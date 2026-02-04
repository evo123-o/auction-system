package org.example.auction.service;

public interface MailService {
    void sendPasswordResetCode(String toEmail, String code);
}
