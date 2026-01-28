package org.example.auction.service;

import org.example.auction.entity.Deposit;

import java.math.BigDecimal;
import java.util.List;

public interface DepositService {

    Deposit createOrUpdate(Long userId, Long itemId, BigDecimal amount);

    Deposit markPaid(Long depositId, String paymentRef);

    boolean isEligibleForBidding(Long userId, Long itemId, BigDecimal requiredAmount);

    void freeze(Long userId, Long itemId);

    void refund(Long userId, Long itemId);

    void forfeit(Long userId, Long itemId);

    /**
     * 获取用户的所有保证金记录
     */
    List<Deposit> listByUser(Long userId);

    /**
     * 获取拍品的所有保证金记录
     */
    List<Deposit> listByItem(Long itemId);

    /**
     * 根据ID获取保证金记录
     */
    Deposit getById(Long depositId);
}
