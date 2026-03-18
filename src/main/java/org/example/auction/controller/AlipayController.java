package org.example.auction.controller;

import com.alipay.api.AlipayApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.example.auction.dto.ApiResponse;
import org.example.auction.entity.Deposit;
import org.example.auction.entity.Order;
import org.example.auction.service.AlipayService;
import org.example.auction.service.DepositService;
import org.example.auction.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付接口
 * 处理支付请求和支付回调通知
 */
@RestController
@RequestMapping("/alipay")
@Tag(name = "支付宝支付", description = "支付宝沙箱支付相关接口")
public class AlipayController {

    private static final Logger logger = LoggerFactory.getLogger(AlipayController.class);

    private final AlipayService alipayService;
    private final DepositService depositService;
    private final OrderService orderService;

    public AlipayController(AlipayService alipayService, DepositService depositService, OrderService orderService) {
        this.alipayService = alipayService;
        this.depositService = depositService;
        this.orderService = orderService;
    }

    /**
     * 支付保证金 - 创建支付宝支付订单
     */
    @Operation(summary = "支付保证金", description = "通过支付宝支付保证金")
    @PostMapping(value = "/deposit/pay/{depositId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> payDeposit(@Parameter(description = "保证金ID") @PathVariable Long depositId) {
        try {
            Deposit deposit = depositService.getById(depositId);
            if (deposit == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("保证金记录不存在");
            }

            if ("PAID".equals(deposit.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("保证金已支付");
            }

            // 生成商户订单号: DEPOSIT-{depositId}-{timestamp}
            String outTradeNo = "DEPOSIT-" + depositId + "-" + System.currentTimeMillis();
            String subject = "拍品保证金";
            String body = "保证金ID: " + depositId + ", 金额: " + deposit.getAmount();

            // 创建支付宝支付表单
            String form = alipayService.createPagePay(outTradeNo, deposit.getAmount(), subject, body);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(form);

        } catch (AlipayApiException e) {
            logger.error("创建保证金支付订单失败: depositId={}", depositId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建支付订单失败: " + e.getMessage());
        }
    }

    /**
     * 支付订单 - 创建支付宝支付订单
     */
    @Operation(summary = "支付订单", description = "通过支付宝支付订单")
    @PostMapping(value = "/order/pay/{orderId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> payOrder(@Parameter(description = "订单ID") @PathVariable Long orderId) {
        try {
            Order order = orderService.getById(orderId);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("订单不存在");
            }

            if ("PAID".equals(order.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("订单已支付");
            }

            // 生成商户订单号: ORDER-{orderId}-{timestamp}
            String outTradeNo = "ORDER-" + orderId + "-" + System.currentTimeMillis();
            String subject = "拍卖订单付款";
            String body = "订单ID: " + orderId + ", 金额: " + order.getFinalPrice();

            // 创建支付宝支付表单
            String form = alipayService.createPagePay(outTradeNo, order.getFinalPrice(), subject, body);

            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(form);

        } catch (AlipayApiException e) {
            logger.error("创建订单支付失败: orderId={}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("创建支付订单失败: " + e.getMessage());
        }
    }

    /**
     * 支付宝异步通知回调
     * 支付宝会在支付成功后向这个接口发送POST请求
     */
    @Operation(summary = "支付宝异步通知", description = "接收支付宝的异步支付通知")
    @PostMapping("/notify")
    public ResponseEntity<String> notifyCallback(HttpServletRequest request) {
        logger.info("收到支付宝异步通知");

        try {
            // 获取支付宝POST过来的所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (String name : requestParams.keySet()) {
                String[] values = requestParams.get(name);
                String valueStr = String.join(",", values);
                params.put(name, valueStr);
            }

            logger.info("支付宝回调参数: {}", params);

            // 验证签名
            boolean signVerified = alipayService.verifyNotify(params);
            if (!signVerified) {
                logger.error("支付宝回调签名验证失败");
                return ResponseEntity.ok("fail");
            }

            // 获取关键参数
            String outTradeNo = params.get("out_trade_no"); // 商户订单号
            String tradeNo = params.get("trade_no"); // 支付宝交易号
            String tradeStatus = params.get("trade_status"); // 交易状态

            logger.info("订单号: {}, 支付宝交易号: {}, 交易状态: {}", outTradeNo, tradeNo, tradeStatus);

            // 只处理支付成功的通知
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 判断是保证金还是订单
                if (outTradeNo.startsWith("DEPOSIT-")) {
                    // 处理保证金支付
                    handleDepositPayment(outTradeNo, tradeNo);
                } else if (outTradeNo.startsWith("ORDER-")) {
                    // 处理订单支付
                    handleOrderPayment(outTradeNo, tradeNo);
                } else {
                    logger.warn("未知的订单类型: {}", outTradeNo);
                }
            }

            // 必须返回success，否则支付宝会不断重试通知
            return ResponseEntity.ok("success");

        } catch (Exception e) {
            logger.error("处理支付宝回调失败", e);
            return ResponseEntity.ok("fail");
        }
    }

    /**
     * 支付宝同步返回页面
     * 用户支付完成后会跳转到这个页面
     */
    @Operation(summary = "支付宝同步返回", description = "支付完成后的同步跳转页面")
    @GetMapping("/return")
    public ResponseEntity<?> returnCallback(HttpServletRequest request) {
        logger.info("收到支付宝同步返回");

        try {
            // 获取支付宝GET过来的所有参数
            Map<String, String> params = new HashMap<>();
            Map<String, String[]> requestParams = request.getParameterMap();
            for (String name : requestParams.keySet()) {
                String[] values = requestParams.get(name);
                String valueStr = String.join(",", values);
                params.put(name, valueStr);
            }

            // 验证签名
            boolean signVerified = alipayService.verifyNotify(params);
            if (!signVerified) {
                logger.error("支付宝同步返回签名验证失败");
                return ResponseEntity.ok(ApiResponse.fail("签名验证失败"));
            }

            String outTradeNo = params.get("out_trade_no");
            String tradeNo = params.get("trade_no");

            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "outTradeNo", outTradeNo,
                    "tradeNo", tradeNo,
                    "message", "支付成功"
            )));

        } catch (Exception e) {
            logger.error("处理支付宝同步返回失败", e);
            return ResponseEntity.ok(ApiResponse.fail("处理支付返回失败"));
        }
    }

    /**
     * 处理保证金支付
     */
    private void handleDepositPayment(String outTradeNo, String tradeNo) {
        try {
            // 从商户订单号中提取保证金ID
            // 格式: DEPOSIT-{depositId}-{timestamp}
            String[] parts = outTradeNo.split("-");
            if (parts.length < 2) {
                logger.error("无效的保证金订单号: {}", outTradeNo);
                return;
            }

            Long depositId = Long.parseLong(parts[1]);
            Deposit deposit = depositService.getById(depositId);

            if (deposit == null) {
                logger.error("保证金记录不存在: depositId={}", depositId);
                return;
            }

            if ("PAID".equals(deposit.getStatus())) {
                logger.info("保证金已支付，跳过处理: depositId={}", depositId);
                return;
            }

            // 标记为已支付
            depositService.markPaid(depositId, tradeNo);
            logger.info("保证金支付成功: depositId={}, tradeNo={}", depositId, tradeNo);

        } catch (Exception e) {
            logger.error("处理保证金支付失败: outTradeNo={}", outTradeNo, e);
        }
    }

    /**
     * 处理订单支付
     */
    private void handleOrderPayment(String outTradeNo, String tradeNo) {
        try {
            // 从商户订单号中提取订单ID
            // 格式: ORDER-{orderId}-{timestamp}
            String[] parts = outTradeNo.split("-");
            if (parts.length < 2) {
                logger.error("无效的订单号: {}", outTradeNo);
                return;
            }

            Long orderId = Long.parseLong(parts[1]);
            Order order = orderService.getById(orderId);

            if (order == null) {
                logger.error("订单不存在: orderId={}", orderId);
                return;
            }

            if ("PAID".equals(order.getStatus())) {
                logger.info("订单已支付，跳过处理: orderId={}", orderId);
                return;
            }

            // 标记为已支付
            orderService.markPaid(orderId);
            logger.info("订单支付成功: orderId={}, tradeNo={}", orderId, tradeNo);

        } catch (Exception e) {
            logger.error("处理订单支付失败: outTradeNo={}", outTradeNo, e);
        }
    }

    /**
     * 测试用：查询订单状态
     */
    @Operation(summary = "查询订单状态", description = "查询支付宝订单状态（测试用）")
    @GetMapping("/query/{outTradeNo}")
    public ResponseEntity<?> queryOrder(@Parameter(description = "商户订单号") @PathVariable String outTradeNo) {
        try {
            Map<String, Object> result = alipayService.queryOrder(outTradeNo);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (AlipayApiException e) {
            logger.error("查询订单失败: outTradeNo={}", outTradeNo, e);
            return ResponseEntity.ok(ApiResponse.fail("查询订单失败: " + e.getMessage()));
        }
    }
}
