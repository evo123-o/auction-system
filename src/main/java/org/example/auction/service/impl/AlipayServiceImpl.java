package org.example.auction.service.impl;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.domain.*;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.*;
import com.alipay.api.response.*;
import org.example.auction.config.AlipayConfig;
import org.example.auction.service.AlipayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付服务实现
 */
@Service
public class AlipayServiceImpl implements AlipayService {

    private static final Logger logger = LoggerFactory.getLogger(AlipayServiceImpl.class);

    private final AlipayClient alipayClient;
    private final AlipayConfig alipayConfig;

    public AlipayServiceImpl(AlipayClient alipayClient, AlipayConfig alipayConfig) {
        this.alipayClient = alipayClient;
        this.alipayConfig = alipayConfig;
    }

    @Override
    public String createPagePay(String outTradeNo, BigDecimal totalAmount, String subject, String body) throws AlipayApiException {
        // 创建API对应的request
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();

        // 设置回调地址
        request.setNotifyUrl(alipayConfig.getNotifyUrl());
        request.setReturnUrl(alipayConfig.getReturnUrl());

        // 设置请求参数
        AlipayTradePagePayModel model = new AlipayTradePagePayModel();
        model.setOutTradeNo(outTradeNo);
        model.setTotalAmount(totalAmount.toString());
        model.setSubject(subject);
        model.setBody(body);
        model.setProductCode("FAST_INSTANT_TRADE_PAY");
        model.setTimeoutExpress("30m"); // 30分钟超时

        request.setBizModel(model);

        // 调用SDK生成表单
        AlipayTradePagePayResponse response = alipayClient.pageExecute(request);

        if (response.isSuccess()) {
            logger.info("创建支付订单成功: outTradeNo={}", outTradeNo);
            return response.getBody();
        } else {
            logger.error("创建支付订单失败: outTradeNo={}, subCode={}, subMsg={}",
                    outTradeNo, response.getSubCode(), response.getSubMsg());
            throw new AlipayApiException("创建支付订单失败: " + response.getSubMsg());
        }
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) throws AlipayApiException {
        // 调用SDK验证签名
        boolean signVerified = AlipaySignature.rsaCheckV1(
                params,
                alipayConfig.getAlipayPublicKey(),
                alipayConfig.getCharset(),
                alipayConfig.getSignType()
        );

        if (signVerified) {
            logger.info("支付宝回调签名验证成功");
        } else {
            logger.warn("支付宝回调签名验证失败");
        }

        return signVerified;
    }

    @Override
    public Map<String, Object> queryOrder(String outTradeNo) throws AlipayApiException {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();

        AlipayTradeQueryModel model = new AlipayTradeQueryModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);

        AlipayTradeQueryResponse response = alipayClient.execute(request);

        Map<String, Object> result = new HashMap<>();
        if (response.isSuccess()) {
            result.put("success", true);
            result.put("tradeNo", response.getTradeNo());
            result.put("outTradeNo", response.getOutTradeNo());
            result.put("tradeStatus", response.getTradeStatus());
            result.put("totalAmount", response.getTotalAmount());
            logger.info("查询订单成功: outTradeNo={}, tradeStatus={}", outTradeNo, response.getTradeStatus());
        } else {
            result.put("success", false);
            result.put("errorMsg", response.getSubMsg());
            logger.error("查询订单失败: outTradeNo={}, subMsg={}", outTradeNo, response.getSubMsg());
        }

        return result;
    }

    @Override
    public void closeOrder(String outTradeNo) throws AlipayApiException {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();

        AlipayTradeCloseModel model = new AlipayTradeCloseModel();
        model.setOutTradeNo(outTradeNo);
        request.setBizModel(model);

        AlipayTradeCloseResponse response = alipayClient.execute(request);

        if (response.isSuccess()) {
            logger.info("关闭订单成功: outTradeNo={}", outTradeNo);
        } else {
            logger.error("关闭订单失败: outTradeNo={}, subMsg={}", outTradeNo, response.getSubMsg());
            throw new AlipayApiException("关闭订单失败: " + response.getSubMsg());
        }
    }

    @Override
    public Map<String, Object> refund(String outTradeNo, BigDecimal refundAmount, String refundReason) throws AlipayApiException {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();

        AlipayTradeRefundModel model = new AlipayTradeRefundModel();
        model.setOutTradeNo(outTradeNo);
        model.setRefundAmount(refundAmount.toString());
        model.setRefundReason(refundReason);
        request.setBizModel(model);

        AlipayTradeRefundResponse response = alipayClient.execute(request);

        Map<String, Object> result = new HashMap<>();
        if (response.isSuccess()) {
            result.put("success", true);
            result.put("tradeNo", response.getTradeNo());
            result.put("outTradeNo", response.getOutTradeNo());
            result.put("refundFee", response.getRefundFee());
            logger.info("退款成功: outTradeNo={}, refundAmount={}", outTradeNo, refundAmount);
        } else {
            result.put("success", false);
            result.put("errorMsg", response.getSubMsg());
            logger.error("退款失败: outTradeNo={}, subMsg={}", outTradeNo, response.getSubMsg());
        }

        return result;
    }
}
