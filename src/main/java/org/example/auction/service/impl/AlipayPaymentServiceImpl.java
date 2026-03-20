package org.example.auction.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.example.auction.config.AlipayProperties;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Order;
import org.example.auction.service.DepositService;
import org.example.auction.service.OrderService;
import org.example.auction.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;

@Service
public class AlipayPaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentServiceImpl.class);

    private final AlipayProperties alipayProperties;
    private final OrderService orderService;
    private final DepositService depositService;

    public AlipayPaymentServiceImpl(AlipayProperties alipayProperties,
                                    OrderService orderService,
                                    DepositService depositService) {
        this.alipayProperties = alipayProperties;
        this.orderService = orderService;
        this.depositService = depositService;
    }

    @Override
    public Map<String, String> createOrderPayInfo(Order order) {
        if (order == null || order.getId() == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!"PENDING_PAYMENT".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("订单状态不允许支付");
        }

        String outTradeNo = "ORDER_" + order.getId() + "_" + System.currentTimeMillis();
        String subject = "拍卖订单支付-" + order.getId();
        BigDecimal amount = order.getFinalPrice() == null ? BigDecimal.ZERO : order.getFinalPrice();
        validateAmount(amount, "订单", String.valueOf(order.getId()));
        String payForm = buildPagePayForm(outTradeNo, amount, subject);

        Map<String, String> result = new HashMap<>();
        result.put("payForm", payForm);
        result.put("outTradeNo", outTradeNo);
        result.put("bizType", "ORDER");
        result.put("bizId", String.valueOf(order.getId()));
        return result;
    }

    @Override
    public Map<String, String> createDepositPayInfo(Deposit deposit) {
        if (deposit == null || deposit.getId() == null) {
            throw new IllegalArgumentException("保证金记录不存在");
        }
        if (!"PENDING".equalsIgnoreCase(deposit.getStatus())) {
            throw new IllegalArgumentException("保证金状态不允许支付，当前状态：" + deposit.getStatus());
        }

        String outTradeNo = "DEPOSIT_" + deposit.getId() + "_" + System.currentTimeMillis();
        String subject = "拍卖保证金支付-" + deposit.getId();
        BigDecimal amount = deposit.getAmount() == null ? BigDecimal.ZERO : deposit.getAmount();
        validateAmount(amount, "保证金", String.valueOf(deposit.getId()));
        String payForm = buildPagePayForm(outTradeNo, amount, subject);

        Map<String, String> result = new HashMap<>();
        result.put("payForm", payForm);
        result.put("outTradeNo", outTradeNo);
        result.put("bizType", "DEPOSIT");
        result.put("bizId", String.valueOf(deposit.getId()));
        return result;
    }

    @Override
    public boolean handleAlipayNotify(Map<String, String> params) {
        try {
            boolean verified = AlipaySignature.rsaCheckV1(
                    params,
                    alipayProperties.getAlipayPublicKey(),
                    alipayProperties.getCharset(),
                    alipayProperties.getSignType()
            );

            if (!verified) {
                return false;
            }

            String tradeStatus = params.get("trade_status");
            if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                return true;
            }

            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");

            if (outTradeNo == null || outTradeNo.isBlank()) {
                return false;
            }

            if (outTradeNo.startsWith("ORDER_")) {
                Long orderId = parseBizId(outTradeNo, "ORDER");
                if (orderId != null) {
                    try {
                        orderService.markPaid(orderId);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } else if (outTradeNo.startsWith("DEPOSIT_")) {
                Long depositId = parseBizId(outTradeNo, "DEPOSIT");
                if (depositId != null) {
                    try {
                        depositService.markPaid(depositId, tradeNo == null ? outTradeNo : tradeNo);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            return true;
        } catch (AlipayApiException e) {
            return false;
        }
    }

    private Long parseBizId(String outTradeNo, String prefix) {
        String marker = prefix + "_";
        if (!outTradeNo.startsWith(marker)) {
            return null;
        }
        String rest = outTradeNo.substring(marker.length());
        String[] parts = rest.split("_");
        if (parts.length == 0) {
            return null;
        }
        try {
            return Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String buildPagePayForm(String outTradeNo, BigDecimal amount, String subject) {
        AlipayClient client = new DefaultAlipayClient(
                alipayProperties.getGatewayUrl(),
                alipayProperties.getAppId(),
                alipayProperties.getMerchantPrivateKey(),
                "json",
                alipayProperties.getCharset(),
                alipayProperties.getAlipayPublicKey(),
                alipayProperties.getSignType()
        );

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());
        request.setReturnUrl(alipayProperties.getReturnUrl());

        String bizContent = "{" +
                "\"out_trade_no\":\"" + escape(outTradeNo) + "\"," +
                "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"," +
                "\"total_amount\":\"" + amount.setScale(2, RoundingMode.HALF_UP) + "\"," +
                "\"subject\":\"" + escape(subject) + "\"" +
                "}";
        request.setBizContent(bizContent);

        try {
            log.info("Create Alipay sandbox page pay: appId={}, outTradeNo={}, amount={}, subject={}",
                    alipayProperties.getAppId(), outTradeNo, amount.setScale(2, RoundingMode.HALF_UP), subject);
            AlipayTradePagePayResponse response = client.pageExecute(request);
            if (!response.isSuccess()) {
                log.error("Create Alipay sandbox pay failed: code={}, subCode={}, msg={}, subMsg={}, outTradeNo={}",
                        response.getCode(), response.getSubCode(), response.getMsg(), response.getSubMsg(), outTradeNo);
                throw new IllegalStateException("创建支付宝支付链接失败: " + response.getSubMsg());
            }
            return response.getBody();
        } catch (AlipayApiException e) {
            log.error("Call Alipay sandbox api exception: outTradeNo={}, errCode={}, errMsg={}, response={}",
                    outTradeNo, e.getErrCode(), e.getErrMsg(), e.getErrMsg(), e);
            throw new IllegalStateException("调用支付宝沙箱失败: " + e.getErrMsg(), e);
        }
    }

    private void validateAmount(BigDecimal amount, String bizType, String bizId) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.compareTo(new BigDecimal("0.01")) < 0) {
            throw new IllegalArgumentException(bizType + "支付金额必须不小于0.01，当前" + bizType + "ID=" + bizId + "，金额=" + normalized);
        }
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
