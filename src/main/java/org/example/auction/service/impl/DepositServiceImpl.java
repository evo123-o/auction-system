package org.example.auction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.auction.entity.Deposit;
import org.example.auction.mapper.DepositMapper;
import org.example.auction.service.DepositService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class DepositServiceImpl implements DepositService {

    private final DepositMapper depositMapper;

    public DepositServiceImpl(DepositMapper depositMapper) {
        this.depositMapper = depositMapper;
    }

    @Override
    @Transactional
    public Deposit createOrUpdate(Long userId, Long itemId, BigDecimal amount) {
        Deposit existing = depositMapper.findByItemAndUser(itemId, userId);
        if (existing == null) {
            existing = Deposit.builder()
                    .userId(userId)
                    .itemId(itemId)
                    .amount(amount)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            depositMapper.insert(existing);
        } else {
            existing.setAmount(amount);
            existing.setUpdatedAt(LocalDateTime.now());
            if ("REFUNDED".equals(existing.getStatus()) || "FORFEITED".equals(existing.getStatus())) {
                existing.setStatus("PENDING");
            }
            depositMapper.updateById(existing);
        }
        return existing;
    }

    @Override
    @Transactional
    public Deposit markPaid(Long depositId, String paymentRef) {
        Deposit d = depositMapper.selectById(depositId);
        if (d == null) throw new IllegalArgumentException("deposit not found: " + depositId);
        
        // 防止重复支付：只有 PENDING 状态才能支付
        if (!"PENDING".equals(d.getStatus())) {
            throw new IllegalArgumentException("保证金状态不允许支付，当前状态：" + d.getStatus());
        }
        
        d.setStatus("PAID");
        d.setPaymentRef(paymentRef);
        d.setUpdatedAt(LocalDateTime.now());
        depositMapper.updateById(d);
        return d;
    }

    @Override
    public boolean isEligibleForBidding(Long userId, Long itemId, BigDecimal requiredAmount) {
        Deposit d = depositMapper.findByItemAndUser(itemId, userId);
        if (d == null) return false;
        if (!"PAID".equals(d.getStatus())) return false;
        return requiredAmount == null || d.getAmount() == null || d.getAmount().compareTo(requiredAmount) >= 0;
    }

    @Override
    @Transactional
    public void freeze(Long userId, Long itemId) {
        Deposit d = depositMapper.findByItemAndUser(itemId, userId);
        if (d == null) throw new IllegalArgumentException("deposit not found");
        d.setStatus("FROZEN");
        d.setUpdatedAt(LocalDateTime.now());
        depositMapper.updateById(d);
    }

    @Override
    @Transactional
    public void refund(Long userId, Long itemId) {
        Deposit d = depositMapper.findByItemAndUser(itemId, userId);
        if (d == null) return;
        d.setStatus("REFUNDED");
        d.setUpdatedAt(LocalDateTime.now());
        depositMapper.updateById(d);
    }

    @Override
    @Transactional
    public void forfeit(Long userId, Long itemId) {
        Deposit d = depositMapper.findByItemAndUser(itemId, userId);
        if (d == null) return;
        d.setStatus("FORFEITED");
        d.setUpdatedAt(LocalDateTime.now());
        depositMapper.updateById(d);
    }

    @Override
    public List<Deposit> listByUser(Long userId) {
        LambdaQueryWrapper<Deposit> qw = new LambdaQueryWrapper<Deposit>()
                .eq(Deposit::getUserId, userId)
                .orderByDesc(Deposit::getCreatedAt);
        return depositMapper.selectList(qw);
    }

    @Override
    public List<Deposit> listByItem(Long itemId) {
        LambdaQueryWrapper<Deposit> qw = new LambdaQueryWrapper<Deposit>()
                .eq(Deposit::getItemId, itemId)
                .orderByDesc(Deposit::getCreatedAt);
        return depositMapper.selectList(qw);
    }

    @Override
    public Deposit getById(Long depositId) {
        return depositMapper.selectById(depositId);
    }
}
