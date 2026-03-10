# 前端接口文档

本文档描述了在线拍卖系统的后端 API 接口。

**API 基础路径**: `/api`

## 0. 接口设计说明

### 0.1 统一响应格式

后端接口统一返回 `ApiResponse<T>` 结构：

```json
{
  "success": true,
  "message": "ok",
  "data": {}
}
```

* `success`: 是否成功
* `message`: 结果描述（失败时返回错误原因）
* `data`: 业务数据（失败时通常为 `null`）

### 0.2 通用请求约定

* JSON 请求需设置 `Content-Type: application/json`
* 受保护接口需携带 `Authorization: Bearer <accessToken>`
* 文件上传使用 `multipart/form-data`

### 0.3 Swagger/OpenAPI 文档

已集成 OpenAPI，启动服务后可通过以下根路径访问文档端点（注意：这些路径不使用 `/api` 前缀）：

* `/swagger-ui/index.html` - 交互式文档
* `/v3/api-docs` - JSON 规范文档

## 1. 认证管理 (Authentication)

### 1.1 用户登录
*   **URL**: `/api/auth/login`
*   **Method**: `POST`
*   **Description**: 使用用户名和密码登录，获取 Access Token 和 Refresh Token。
*   **Request Body**:
    ```json
    {
      "username": "user1",
      "password": "password123"
    }
    ```
*   **Response (Success)**:
    ```json
    {
      "success": true,
      "message": "ok",
      "data": {
        "accessToken": "Bearer eyJhbGciOiJIUzI1NiJ9...",
        "expiresIn": 3600,
        "refreshToken": "uuid-string",
        "user": {
          "id": 1,
          "username": "user1",
          "email": "user@example.com",
          "role": "ADMIN",
          "creditScore": 100,
          "status": "ACTIVE"
        }
      }
    }
    ```

### 1.2 用户注册
*   **URL**: `/api/auth/register`
*   **Method**: `POST`
*   **Description**: 注册新用户。
*   **Request Body**:
    ```json
    {
      "username": "new user",
      "password": "password123",
      "email": "user@example.com"
    }
    ```
*   **Response**:
    ```json
    {
      "success": true,
      "message": "ok",
      "data": "new user"
    }
    ```

### 1.3 刷新令牌
*   **URL**: `/api/auth/refresh`
*   **Method**: `POST`
*   **Description**: 使用 Refresh Token 获取新的 Access Token。
*   **Query Parameters**:
    *   `refreshToken`: 原有的 refresh token 字符串
*   **Response**:
    ```json
    {
      "success": true,
      "message": "ok",
      "data": {
        "accessToken": "Bearer new-token...",
        "expiresIn": 3600,
        "refreshToken": "new-refresh-token..." 
      }
    }
    ```

### 1.4 用户登出
*   **URL**: `/api/auth/logout`
*   **Method**: `POST`
*   **Description**: 使当前 Access Token 失效，可选撤销 Refresh Token。
*   **Headers**: `Authorization: Bearer <token>`
*   **Query Parameters**:
    *   `refreshToken` (可选): 需要撤销的 refresh token
*   **Response**: `200 OK`

### 1.5 忘记密码
*   **URL**: `/api/auth/password/forgot`
*   **Method**: `POST`
*   **Description**: 发起密码重置（发送验证码）
*   **Request Body**:
    ```json
    {
        "usernameOrEmail": "user1"
    }
    ```

### 1.6 重置密码
*   **URL**: `/api/auth/password/reset`
*   **Method**: `POST`
*   **Description**: 使用验证码重置密码
*   **Request Body**:
    ```json
    {
        "username": "user1",
        "code": "123456",
        "newPassword": "newPassword123"
    }
    ```

## 2. 拍品管理 (Items)

### 2.1 获取拍品列表 (分页)
*   **URL**: `/api/items`
*   **Method**: `GET`
*   **Query Parameters**:
    *   `page`: 页码 (默认1)
    *   `size`: 每页数量 (默认10)
    *   `title`: 标题关键字 (可选)
    *   `category`: 分类 (可选)
    *   `status`: 状态 (PENDING, ON_SHELF, RUNNING, SOLD, CLOSED)
*   **Response**:
    ```json
    {
      "success": true,
      "message": "ok",
      "data": {
        "records": [ { "id": 1, "title": "Sample Item", "currentPrice": 100.0 } ],
        "total": 50,
        "pages": 5,
        "current": 1,
        "size": 10
      }
    }
    ```

### 2.2 获取拍品详情
*   **URL**: `/api/items/{id}`
*   **Method**: `GET`
*   **Response**: `ItemDTO` 对象

### 2.3 创建拍品 (需登录)
*   **URL**: `/api/items`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "title": "古董花瓶",
      "category": "古董",
      "description": "明代...",
      "startPrice": 100.00,
      "depositAmount": 20.00,
      "startTime": "2023-10-01T10:00:00",  
      "endTime": "2023-10-02T10:00:00",
      "durationMinutes": 120,     
      "autoExtension": true
    }
    ```
    注：
    1. `startTime` 为用户期望的开始时间（可选），实际开拍时间以管理员审核时系统设定为准（审核通过瞬间开始，持续 `durationMinutes`）。
    2. 发布拍品需要用户信用分达到系统配置的最低要求（默认60分）。

### 2.4 更新拍品
*   **URL**: `/api/items/{id}`
*   **Method**: `PUT`
*   **Request Body**: 同创建接口
*   **Note**: 仅创建者或管理员可操作

### 2.5 删除拍品
*   **URL**: `/api/items/{id}`
*   **Method**: `DELETE`
*   **Note**: 仅创建者或管理员可操作

### 2.6 上传拍品图片
*   **URL**: `/api/items/{id}/image`
*   **Method**: `POST`
*   **Content-Type**: `multipart/form-data`
*   **Form Data**:
    *   `file`: (Binary File)
*   **Note**: 仅创建者或管理员可操作

### 2.7 开始拍卖
*   **URL**: `/api/items/{id}/start`
*   **Method**: `POST`
*   **Description**: 将拍品状态置为 RUNNING，仅创建者或管理员可操作。

### 2.8 审核拍品 (需 ADMIN)
*   **URL**: `/api/items/{id}/audit`
*   **Method**: `POST`
*   **Description**: 管理员审核拍品。按系统配置（当前实现 - 方案一），审核通过时系统会：
  - 将 `startTime` 设为审核通过时刻（now）
  - 将 `endTime` 设为 `startTime + durationMinutes`
  - 将状态置为 `RUNNING`（立即开始拍卖），若审核不通过则状态置为 `REJECTED`。
*   **Request Body**:
    ```json
    {
      "approved": true,
      "reason": "通过申请"
    }
    ```

## 3. 竞拍管理 (Bids)

### 3.1 提交出价
*   **URL**: `/api/bids/place`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "itemId": 1,
      "amount": 120.00
    }
    ```

### 3.2 提交出价 (快捷路径)
*   **URL**: `/api/items/{id}/bid`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "amount": 120.00
    }
    ```

### 3.3 查询出价历史
*   **URL**: `/api/bids/history`
*   **Method**: `GET`
*   **Query Parameters**:
    *   `itemId`: 拍品ID
*   **Response**: 出价记录列表

## 4. 保证金管理 (Deposits)

### 4.1 初始化保证金
*   **URL**: `/api/deposits/init/{itemId}`
*   **Method**: `POST`
*   **Description**: 为某拍品创建保证金记录（状态为 PENDING）。

### 4.2 支付保证金 (模拟)
*   **URL**: `/api/deposits/pay/{depositId}`
*   **Method**: `POST`
*   **Description**: 模拟支付成功。

### 4.3 查询保证金资格
*   **URL**: `/api/deposits/status`
*   **Method**: `GET`
*   **Query Parameters**:
    *   `itemId`: 拍品ID
*   **Response**: `{ "eligible": true/false }`

### 4.4 我的保证金列表
*   **URL**: `/api/deposits/my`
*   **Method**: `GET`

### 4.5 获取保证金详情
*   **URL**: `/api/deposits/{depositId}`
*   **Method**: `GET`

## 5. 订单管理 (Orders)

### 5.1 获取订单详情
*   **URL**: `/api/orders/{id}`
*   **Method**: `GET`
*   **Description**: 仅买家、卖家或管理员可查看。
*   **Response（扩展字段）**： 为支持违约/发货超时功能，订单详情中会包含如下可选字段：
    - `breachedAt` (可选)：若订单被判定为违约，返回违约发生时间（ISO-8601 字符串）；
    - `breachRecords` (可选)：数组，包含该订单相关的违约记录（见第 8 节），每条记录至少包含 `reason`, `penaltyAmount`, `creditScoreDelta`, `createdAt`。

### 5.2 我的订单 (买家视角)
*   **URL**: `/api/orders/my/buyer`
*   **Method**: `GET`
*   **Query Parameters**: `page`, `size`, `status`

### 5.3 我的订单 (卖家视角)
*   **URL**: `/api/orders/my/seller`
*   **Method**: `GET`
*   **Query Parameters**: `page`, `size`, `status`

### 5.4 支付订单 (模拟)
*   **URL**: `/api/orders/pay/{id}`
*   **Method**: `POST`
*   **Description**: 模拟支付订单，状态变为 PAID。

### 5.5 发货
*   **URL**: `/api/orders/ship/{id}`
*   **Method**: `POST`
*   **Description**: 卖家或管理员标记发货。如果订单已经被系统判定为 `BREACH`（违约），该接口应返回错误并禁止发货操作。

### 5.6 确认收货
*   **URL**: `/api/orders/receive/{id}`
*   **Method**: `POST`
*   **Description**: 买家确认收货。

### 5.7 查看订单凭证
*   **URL**: `/api/orders/{id}/receipt`
*   **Method**: `GET`

### 5.8 管理员查询所有订单
*   **URL**: `/api/orders/admin/all`
*   **Method**: `GET`
*   **Query Parameters**: `page`, `size`, `status`
*   **Note**: 需 ADMIN 角色。

### 5.9 发货超时惩罚（系统功能说明）
* **功能概述**：系统通过定时任务扫描在规定时间内未发货的已支付订单（默认 72 小时），自动对卖家执行“发货超时惩罚”：将订单状态置为 `BREACH`、记录违约、按配置扣减卖家信用分并（可选）对保证金做冻结或没收等处理，并通知交易双方。
* **触发方式**：后端有定时器 `OverdueShippingScheduler`（cron：`0 */5 * * * *`，每 5 分钟扫描一次），使用 `OrderMapper.findOverdueToShip(hours)` 查询满足条件的订单（默认 `hours=72`，可配置）。
* **相关配置（application.properties）**：
  - `app.shipping.ship-by-hours`（int，小时，默认 72）—— 判定发货超时的阈值；
  - `app.breach.shipping.credit-deduction`（int，默认 10）—— 发货违约扣减卖家信用分；
  - `app.breach.shipping.deposit-action`（枚举：`FORFEIT` | `FREEZE` | `NONE`，默认 `NONE`）—— 发货违约时对保证金的处理策略（注意：卖家通常没有保证金字段，默认 NONE）。
* **前端显示/行为建议**：
  - 订单列表与详情需展示 `BREACH` 状态及 `breachedAt`；若订单为 `BREACH`，禁用发货操作按钮并在详情页展示违约记录；
  - 推荐前端订阅通知（WebSocket/SSE）以实时反映违约事件。

## 6. 评价管理 (Evaluations)

### 6.1 获取订单评价
*   **URL**: `/api/evaluations/order/{orderId}`
*   **Method**: `GET`

### 6.2 我的评价列表
*   **URL**: `/api/evaluations/my`
*   **Method**: `GET`
*   **Description**: 获取当前用户做出的评价。

### 6.3 创建评价
*   **URL**: `/api/evaluations`
*   **Method**: `POST`
*   **Description**: 仅交易双方可评价，且订单需完成。
*   **Request Body**:
    ```json
    {
      "orderId": 1,
      "rating": 5,
      "comment": "Good!"
    }
    ```

### 6.4 检查是否已评价
*   **URL**: `/api/evaluations/check/{orderId}`
*   **Method**: `GET`
*   **Response**: `{ "hasReviewed": true/false }`

## 7. 物流管理 (Logistics)

### 7.1 获取物流信息
*   **URL**: `/api/logistics/{orderId}`
*   **Method**: `GET`

### 7.2 录入物流信息
*   **URL**: `/api/logistics/{orderId}`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "company": "SF Express",
      "trackingNo": "SF123456",
      "notes": "..."
    }
    ```

## 8. 违约记录 (Breaches)

### 8.1 我的违约记录
*   **URL**: `/api/breaches`
*   **Method**: `GET`
*   **Description**: 查询当前用户的违约记录列表（包含买家未付款违约和卖家未发货违约）。
*   **返回字段**：`id`, `userId`, `itemId`, `orderId`, `reason`, `penaltyAmount`, `creditScoreDelta`, `createdAt`。

## 9. 管理员接口 (Admin)

### 9.1 用户管理 (需 ADMIN)
*   **Base URL**: `/api/admin/users`

#### 9.1.1 分页查询用户
*   **URL**: `/api/admin/users`
*   **Method**: `GET`
*   **Query Parameters**: `page` (默认1), `size` (默认20)
*   **Response**: 包含 `creditScore` 字段。

#### 9.1.2 获取用户详情
*   **URL**: `/api/admin/users/{id}`
*   **Method**: `GET`
*   **Response**: 包含 `creditScore` 字段。

#### 9.1.3 创建用户
*   **URL**: `/api/admin/users`
*   **Method**: `POST`
*   **Request Body**:
    ```json
    {
      "username": "user_name",
      "password": "password123",
      "email": "user@example.com",
      "roles": ["ADMIN"]
    }
    ```

#### 9.1.4 更新用户
*   **URL**: `/api/admin/users/{id}`
*   **Method**: `PUT`
*   **Description**: 更新用户权限(roles)、状态(enabled)、邮箱、信用分或重置密码。
*   **Request Body**:
    ```json
    {
      "role": "USER",
      "enabled": true,
      "email": "newemail@example.com",
      "password": "newpassword123",
      "creditScore": 100
    }
    ```

#### 9.1.5 删除用户
*   **URL**: `/api/admin/users/{id}`
*   **Method**: `DELETE`

### 9.2 竞拍管理 (需 ADMIN)
*   **List Bids (GET)**: `/api/admin/bids?page=1&size=20`
*   **Cancel Bid (DELETE)**: `/api/admin/bids/{id}`

### 9.3 订单管理/违约干预 (需 ADMIN)
#### 9.3.1 手动触发发货超时惩罚
*   **URL**: `/api/admin/orders/{id}/force-shipping-breach`
*   **Method**: `POST`
*   **Description**: 仅管理员可调用。对指定订单立即执行发货超时惩罚处理（通常用于测试、调试或手动干预）。
*   **Response**: 返回更新后的订单信息（状态变为 `BREACH`）。

#### 9.3.2 撤销发货超时惩罚
*   **URL**: `/api/admin/orders/{id}/revoke-shipping-breach`
*   **Method**: `POST`
*   **Description**: 仅管理员可调用。撤销指定订单的发货超时惩罚。
    *   **效果**：
        1. 订单状态从 `BREACH` 回滚为 `PAID`（允许卖家重新发货）。
        2. 卖家信用分回滚（加回被扣除的分数）。
        3. 对应的违约记录被删除（或标记为已撤销）。
*   **Response**: 返回更新后的订单信息（状态变为 `PAID`）。

## 10. 配置项与监控（重要）

### 10.1 新增相关配置（application.properties）
```
# 发货超时与违约惩罚
app.shipping.ship-by-hours=72
app.breach.shipping.credit-deduction=10
app.breach.shipping.deposit-action=NONE

# 买家未付款违约
app.breach.payment.credit-deduction=10
app.breach.payment.deposit-action=FORFEIT
```

### 10.2 邮件相关健康检查/超时建议
当应用启用邮件（SMTP）并且 Spring Boot 的 mail 健康检查开启时，应用会在健康检查中尝试连接 SMTP 服务器。如果 SMTP 响应较慢或基本认证被阻止，HealthIndicator 可能会报告延迟或失败。

推荐在 `application.properties` 中添加如下 SMTP 超时设置或关闭 mail 健康检查以避免启动/健康检查时阻塞：
```
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000
spring.mail.properties.mail.smtp.writetimeout=5000
# 如不需要 mail 健康检查：
management.health.mail.enabled=false
```

## 11. 安全设计 (Security Design)

* **用户认证**: 使用 JWT 作为 Access Token，通过 `Authorization: Bearer <token>` 传递；Refresh Token 用于换取新的 Access Token。
* **授权控制**: Spring Security 统一拦截除 `/api/auth/**`、Swagger、静态资源外的请求；管理员接口需 `ADMIN` 角色；拍品/订单等资源在服务层校验所有者或管理员权限。
* **数据加密**:
  * 密码使用 BCrypt 哈希存储（`PasswordEncoder`）
  * JWT 使用 HS256 签名密钥，防止被篡改；请通过 `jwt.secret` 配置强随机密钥
  * 若部署为多服务验证场景，可考虑迁移至 RS256，使用公钥验证而不暴露签名密钥
  * 密钥轮换会使现有 token 失效，建议在维护窗口进行；当前实现仅支持单密钥校验，如需旧/新密钥并行校验需扩展 `JwtTokenUtil`
  * 生产环境建议通过 HTTPS 传输，保护令牌与敏感数据
* **接口安全**:
  * 无状态认证（`SessionCreationPolicy.STATELESS`），CSRF 在 JWT 场景下禁用
  * Token 黑名单支持 Redis 存储，登出后可撤销访问权限
  * 使用 Bean Validation（`@Valid`）进行输入校验，避免非法参数
  * CORS 当前配置为 `allowedOriginPatterns("*")`（仅适用于开发环境），生产部署前需在 `WebMvcConfig#addCorsMappings` 中限制可信域名（建议使用 `allowedOrigins("https://example.com")` 或明确的域名列表）

---

