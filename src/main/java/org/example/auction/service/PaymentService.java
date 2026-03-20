package org.example.auction.service;

import java.util.Map;

import org.example.auction.entity.Deposit;
import org.example.auction.entity.Order;

public interface PaymentService {

    Map<String, String> createOrderPayInfo(Order order);

    Map<String, String> createDepositPayInfo(Deposit deposit);

    boolean handleAlipayNotify(Map<String, String> params);
}
