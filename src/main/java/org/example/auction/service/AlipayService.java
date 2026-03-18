package org.example.auction.service;

import com.alipay.api.AlipayApiException;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付宝支付服务接口
 */
public interface AlipayService {

    /**
     * 创建网页支付订单
     * @param outTradeNo 商户订单号
     * @param totalAmount 订单金额
     * @param subject 订单标题
     * @param body 订单描述
     * @return 支付表单HTML
     * @throws AlipayApiException
     */
    String createPagePay(String outTradeNo, BigDecimal totalAmount, String subject, String body) throws AlipayApiException;

    /**
     * 验证支付宝回调签名
     * @param params 回调参数
     * @return 签名是否有效
     * @throws AlipayApiException
     */
    boolean verifyNotify(Map<String, String> params) throws AlipayApiException;

    /**
     * 查询订单支付状态
     * @param outTradeNo 商户订单号
     * @return 订单信息
     * @throws AlipayApiException
     */
    Map<String, Object> queryOrder(String outTradeNo) throws AlipayApiException;

    /**
     * 关闭订单
     * @param outTradeNo 商户订单号
     * @throws AlipayApiException
     */
    void closeOrder(String outTradeNo) throws AlipayApiException;

    /**
     * 退款
     * @param outTradeNo 商户订单号
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @return 退款结果
     * @throws AlipayApiException
     */
    Map<String, Object> refund(String outTradeNo, BigDecimal refundAmount, String refundReason) throws AlipayApiException;
}
