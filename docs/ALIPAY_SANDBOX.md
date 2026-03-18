# 支付宝沙箱功能使用说明

## 概述

本系统已集成支付宝沙箱支付功能，支持保证金和订单的在线支付。

## 配置信息

沙箱环境配置位于 `src/main/resources/application.properties`:

```properties
# 支付宝应用ID
alipay.app-id=9021000162620309

# 商户私钥（RSA2）
alipay.merchant-private-key=MIIEvQIBADANBgk...

# 支付宝公钥（用于验证签名）
alipay.alipay-public-key=MIIBIjANBgkqhki...

# 沙箱网关地址
alipay.gateway-url=https://openapi.alipaydev.com/gateway.do

# 签名类型
alipay.sign-type=RSA2

# 字符编码
alipay.charset=utf-8

# 异步通知地址（支付宝回调）
alipay.notify-url=http://a679b976.natappfree.cc/alipay/notify

# 同步返回地址（支付完成后跳转）
alipay.return-url=http://a679b976.natappfree.cc/auction/order-result
```

## API 接口

### 1. 保证金支付

#### 1.1 支付宝支付保证金
- **接口**: `POST /api/deposits/pay/alipay/{depositId}`
- **描述**: 创建支付宝支付订单，返回支付表单HTML
- **响应**: HTML格式的支付表单（可直接在浏览器中打开）
- **需要认证**: 是

示例请求：
```bash
curl -X POST http://localhost:8080/api/deposits/pay/alipay/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### 1.2 模拟支付保证金（测试用）
- **接口**: `POST /api/deposits/pay/{depositId}`
- **描述**: 模拟支付（不经过支付宝）
- **需要认证**: 是

### 2. 订单支付

#### 2.1 支付宝支付订单
- **接口**: `POST /api/orders/pay/alipay/{orderId}`
- **描述**: 创建支付宝支付订单，返回支付表单HTML
- **响应**: HTML格式的支付表单（可直接在浏览器中打开）
- **需要认证**: 是

示例请求：
```bash
curl -X POST http://localhost:8080/api/orders/pay/alipay/1 \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### 2.2 模拟支付订单（测试用）
- **接口**: `POST /api/orders/pay/{orderId}`
- **描述**: 模拟支付（不经过支付宝）
- **需要认证**: 是

### 3. 支付宝回调接口

#### 3.1 异步通知
- **接口**: `POST /alipay/notify`
- **描述**: 支付宝服务器异步通知支付结果
- **需要认证**: 否（公开接口，但需要验证签名）
- **注意**: 必须返回 "success" 字符串，否则支付宝会重复通知

#### 3.2 同步返回
- **接口**: `GET /alipay/return`
- **描述**: 用户支付完成后的同步跳转页面
- **需要认证**: 否（公开接口，但需要验证签名）

#### 3.3 查询订单状态
- **接口**: `GET /alipay/query/{outTradeNo}`
- **描述**: 查询支付宝订单状态
- **需要认证**: 否

示例：
```bash
curl http://localhost:8080/alipay/query/DEPOSIT-1-1234567890
```

## 支付流程

### 保证金支付流程

1. 用户初始化保证金：`POST /api/deposits/init/{itemId}`
2. 获取保证金ID，发起支付：`POST /api/deposits/pay/alipay/{depositId}`
3. 前端接收到HTML表单，自动提交或在新窗口打开
4. 用户在支付宝页面完成支付
5. 支付宝异步通知：`POST /alipay/notify` （系统自动处理）
6. 支付成功后跳转：`GET /alipay/return`
7. 系统自动更新保证金状态为 PAID

### 订单支付流程

1. 拍卖结束后自动生成订单
2. 买家发起支付：`POST /api/orders/pay/alipay/{orderId}`
3. 前端接收到HTML表单，自动提交或在新窗口打开
4. 用户在支付宝页面完成支付
5. 支付宝异步通知：`POST /alipay/notify` （系统自动处理）
6. 支付成功后跳转：`GET /alipay/return`
7. 系统自动更新订单状态为 PAID，并处理保证金

## 商户订单号格式

- **保证金**: `DEPOSIT-{depositId}-{timestamp}`
  - 例如: `DEPOSIT-123-1710752400000`

- **订单**: `ORDER-{orderId}-{timestamp}`
  - 例如: `ORDER-456-1710752400000`

## 安全机制

1. **签名验证**: 所有支付宝回调都会进行RSA2签名验证
2. **幂等性**: 系统会检查订单状态，防止重复支付
3. **权限控制**: 用户只能支付自己的保证金和订单
4. **超时设置**: 支付订单30分钟后自动过期

## 沙箱测试账号

支付宝沙箱环境提供测试买家账号：
- 登录地址: https://openhome.alipay.com/platform/appDaily.htm
- 使用沙箱账号信息进行测试

## 前端集成示例

### React/Vue 示例

```javascript
// 发起支付
async function payWithAlipay(depositId) {
  try {
    const response = await fetch(`/api/deposits/pay/alipay/${depositId}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const htmlForm = await response.text();

    // 方式1: 在新窗口打开
    const newWindow = window.open('', '_blank');
    newWindow.document.write(htmlForm);
    newWindow.document.close();

    // 方式2: 在当前页面打开
    // document.write(htmlForm);
    // document.close();

  } catch (error) {
    console.error('支付失败:', error);
  }
}
```

## 日志监控

系统会记录所有支付相关日志：
- 支付订单创建
- 签名验证结果
- 支付成功/失败
- 回调处理结果

查看日志：
```bash
tail -f logs/application.log | grep -i alipay
```

## 常见问题

### Q1: 支付后没有回调？
A: 检查 notify-url 是否可以从外网访问。可以使用 natapp 等内网穿透工具。

### Q2: 签名验证失败？
A: 检查公钥是否正确配置，确保使用的是支付宝公钥而不是应用公钥。

### Q3: 支付表单无法显示？
A: 确保响应的 Content-Type 是 text/html，并且前端正确处理HTML响应。

### Q4: 如何切换到生产环境？
A: 修改 application.properties 中的以下配置：
- gateway-url: 改为生产网关 https://openapi.alipay.com/gateway.do
- app-id: 使用正式应用的APPID
- 私钥和公钥: 使用正式应用的密钥对
- notify-url 和 return-url: 使用正式域名

## 技术栈

- **Alipay SDK**: 4.34.0.ALL
- **签名算法**: RSA2
- **字符编码**: UTF-8
- **支付产品**: 电脑网站支付 (FAST_INSTANT_TRADE_PAY)

## 参考文档

- [支付宝开放平台](https://open.alipay.com/)
- [沙箱环境说明](https://opendocs.alipay.com/open/200/105311)
- [电脑网站支付API](https://opendocs.alipay.com/open/270/105898)
