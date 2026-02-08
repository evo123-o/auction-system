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

已集成 OpenAPI，启动服务后可通过以下根路径访问端点（注意：这些路径不使用 `/api` 前缀）：

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
        "refreshToken": "uuid-string"
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
        "records": [ { "id": 1, "title": "...", "currentPrice": 100.0, "..."} ],
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
      "autoExtension": true
    }
    ```

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
*   **Description**: 卖家或管理员标记发货。

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

## 9. 管理员接口 (Admin)

### 9.1 用户管理
*   **Base URL**: `/api/admin/users`
*   **List (GET)**: `/api/admin/users?page=1&size=20`
*   **Detail (GET)**: `/api/admin/users/{id}`
*   **Create (POST)**: `/api/admin/users`
*   **Update (PUT)**: `/api/admin/users/{id}`
*   **Delete (DELETE)**: `/api/admin/users/{id}`

### 9.2 竞拍管理
*   **List Bids (GET)**: `/api/admin/bids?page=1&size=20`
*   **Cancel Bid (DELETE)**: `/api/admin/bids/{id}`

## 10. 安全设计 (Security Design)

* **用户认证**: 使用 JWT 作为 Access Token，通过 `Authorization: Bearer <token>` 传递；Refresh Token 用于换取新的 Access Token。
* **授权控制**: Spring Security 统一拦截除 `/api/auth/**`、Swagger、静态资源外的请求；管理员接口需 `ADMIN` 角色；拍品/订单等资源在服务层校验所有者或管理员权限。
* **数据加密**:
  * 密码使用 BCrypt 哈希存储（`PasswordEncoder`）
  * JWT 使用 HS256 签名密钥，防止被篡改；请通过 `jwt.secret` 配置强随机密钥
  * 若部署为多服务验证场景，可考虑迁移至 RS256，使用公钥验证而不暴露签名密钥
  * 密钥轮换会使现有 token 失效，建议在维护窗口进行，或在 `JwtTokenUtil` 中支持旧/新密钥并行校验实现过渡
  * 生产环境建议通过 HTTPS 传输，保护令牌与敏感数据
* **接口安全**:
  * 无状态认证（`SessionCreationPolicy.STATELESS`），CSRF 在 JWT 场景下禁用
  * Token 黑名单支持 Redis 存储，登出后可撤销访问权限
  * 使用 Bean Validation（`@Valid`）进行输入校验，避免非法参数
  * CORS 当前配置为 `allowedOriginPatterns("*")`（仅适用于开发环境），生产部署前需在 `WebMvcConfig#addCorsMappings` 中限制可信域名（建议使用 `allowedOrigins("https://example.com")` 或明确的域名列表）
