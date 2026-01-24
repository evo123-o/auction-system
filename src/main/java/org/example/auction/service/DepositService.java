package org.example.auction.service;

import org.example.auction.entity.Deposit;

import java.math.BigDecimal;

public interface DepositService {

    Deposit createOrUpdate(Long userId, Long itemId, BigDecimal amount);

    Deposit markPaid(Long depositId, String paymentRef);

    boolean isEligibleForBidding(Long userId, Long itemId, BigDecimal requiredAmount);

    void freeze(Long userId, Long itemId);

    void refund(Long userId, Long itemId);

    void forfeit(Long userId, Long itemId);
}
