# 拍卖系统 (Auction System)

基于 Spring Boot 和 Vue3 的拍卖系统。

## 技术栈

### 后端
- Spring Boot 4.0.1
- Spring Security + JWT 认证
- MyBatis Plus
- MySQL 8+
- Redis

### 前端
- Vue 3
- Vite
- Vue Router 4
- Pinia (状态管理)
- Axios

## 项目结构

```
auction-system/
├── src/                    # 后端源码 (Spring Boot)
│   ├── main/
│   │   ├── java/
│   │   └── resources/
│   └── test/
├── frontend/               # 前端源码 (Vue3)
│   ├── src/
│   │   ├── api/           # API 接口
│   │   ├── views/         # 页面组件
│   │   ├── components/    # 公共组件
│   │   ├── router/        # 路由配置
│   │   ├── stores/        # 状态管理
│   │   └── App.vue
│   └── package.json
└── pom.xml                # Maven 配置
```

## 系统整体架构

前端独立部署，通过 HTTP/HTTPS 调用后端 API，实现前后端分离。

```mermaid
flowchart LR
  subgraph Frontend[前端 (Vue 3)]
    UI[浏览器/客户端]
  end

  subgraph Backend[后端 API (Spring Boot)]
    API[REST API]
    Auth[安全认证/JWT]
    Service[业务服务]
  end

  subgraph DataLayer[数据层]
    MySQL[(MySQL 数据库)]
    Redis[(Redis 缓存/Token 黑名单)]
  end

  subgraph ThirdParty[第三方服务]
    Mail[邮件服务/SMTP]
    Storage[文件存储 (本地/对象存储)]
  end

  UI -- HTTP/HTTPS --> API
  API --> Auth
  API --> Service
  Service --> MySQL
  Service --> Redis
  Service --> Mail
  Service --> Storage
```

## 快速开始

### 1. 环境要求

- Java 17+
- Node.js 20+
- MySQL 8+
- Redis
- Maven 3.8+

### 2. 后端设置

#### 2.1 配置数据库

创建数据库并执行 schema:

```bash
mysql -u root -p
CREATE DATABASE auction_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE auction_db;
SOURCE src/main/resources/schema.sql;
```

#### 2.2 配置 application.properties

在 `src/main/resources/application.properties` 中配置数据库连接:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/auction_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=123456
```

#### 2.3 创建测试用户 (可选)

在数据库中插入测试用户:

```sql
-- 密码是 "password123" 的 BCrypt 哈希
INSERT INTO users (username, password, email, role, credit_score, status) 
VALUES ('testuser', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'test@example.com', 'USER', 100, 'ACTIVE');

INSERT INTO users (username, password, email, role, credit_score, status) 
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'admin@example.com', 'ADMIN', 100, 'ACTIVE');
```

#### 2.4 启动后端

```bash
# 使用 Maven
mvn spring-boot:run

# 或者先编译再运行
mvn clean package -DskipTests
java -jar target/auction-0.0.1-SNAPSHOT.jar
```

后端将在 `http://localhost:8080` 运行。

### 3. 前端设置

#### 3.1 安装依赖

```bash
cd frontend
npm install
```

#### 3.2 启动开发服务器

```bash
npm run dev
```

前端将在 `http://localhost:3000` 运行。

#### 3.3 构建生产版本

```bash
npm run build
```

构建后的文件将在 `frontend/dist` 目录中。

## 功能特性

### 已实现功能

1. **用户认证**
   - 登录/登出
   - JWT Token 认证
   - Refresh Token 机制
   - 自动 token 刷新
   - 忘记密码 / 邮件验证码重置密码

2. **拍品管理**
   - 浏览拍品列表（分页、关键字/分类/状态筛选）
   - 查看拍品详情
   - 创建新拍品
   - 编辑拍品信息
   - 删除拍品
   - 上传拍品图片
   - 管理员审核拍品（通过/拒绝）
   - 开始/停止拍卖

3. **竞拍功能**
   - 对拍品出价
   - 查看出价历史
   - 自动延时功能（临近结束时自动延长拍卖时间）

4. **保证金管理**
   - 初始化保证金记录
   - 模拟支付保证金
   - 查询竞拍资格
   - 查看我的保证金列表

5. **订单管理**
   - 查看订单详情
   - 买家/卖家视角查看订单列表
   - 模拟支付订单
   - 卖家发货、买家确认收货
   - 查看/导出 HTML 及 PDF 订单凭证

6. **物流管理**
   - 录入快递公司和单号
   - 查询物流信息

7. **评价管理**
   - 交易完成后买卖双方互评（1-5 星 + 文字评论）
   - 查看订单评价列表
   - 检查是否已评价

8. **违约记录**
   - 查看本人违约记录

9. **管理员功能**
   - 用户管理（分页查询、创建、更新、删除）
   - 竞拍管理（查看所有出价、取消出价）
   - 所有订单查询

## API 端点

完整接口文档请参阅 [API_DOCUMENTATION.md](./API_DOCUMENTATION.md)，或启动服务后访问 `http://localhost:8080/swagger-ui/index.html`。

### 认证相关
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `POST /api/auth/refresh` - 刷新 token
- `POST /api/auth/password/forgot` - 忘记密码（发送验证码）
- `POST /api/auth/password/reset` - 重置密码

### 拍品相关
- `GET /api/items` - 获取拍品列表（分页）
- `GET /api/items/{id}` - 获取拍品详情
- `POST /api/items` - 创建拍品
- `PUT /api/items/{id}` - 更新拍品
- `DELETE /api/items/{id}` - 删除拍品
- `POST /api/items/{id}/image` - 上传拍品图片
- `POST /api/items/{id}/start` - 开始拍卖
- `POST /api/items/{id}/stop` - 停止拍卖
- `POST /api/items/{id}/audit` - 审核拍品（管理员）

### 竞拍相关
- `POST /api/bids/place` - 出价
- `POST /api/items/{id}/bid` - 对指定拍品出价（快捷路径）
- `GET /api/bids/history` - 查看出价历史

### 保证金相关
- `POST /api/deposits/init/{itemId}` - 初始化保证金
- `POST /api/deposits/pay/{depositId}` - 支付保证金
- `GET /api/deposits/status` - 查询竞拍资格
- `GET /api/deposits/my` - 我的保证金列表
- `GET /api/deposits/{depositId}` - 保证金详情

### 订单相关
- `GET /api/orders/{id}` - 获取订单详情
- `GET /api/orders/my/buyer` - 我的订单（买家）
- `GET /api/orders/my/seller` - 我的订单（卖家）
- `POST /api/orders/pay/{id}` - 支付订单
- `POST /api/orders/ship/{id}` - 发货
- `POST /api/orders/receive/{id}` - 确认收货
- `GET /api/orders/{id}/receipt` - 查看订单凭证（HTML）
- `GET /api/orders/{id}/receipt/pdf` - 导出订单凭证（PDF）
- `GET /api/orders/admin/all` - 所有订单（管理员）

### 物流相关
- `GET /api/logistics/{orderId}` - 获取物流信息
- `POST /api/logistics/{orderId}` - 录入物流信息

### 评价相关
- `GET /api/evaluations/order/{orderId}` - 获取订单评价
- `GET /api/evaluations/my` - 我的评价列表
- `POST /api/evaluations` - 创建评价
- `GET /api/evaluations/check/{orderId}` - 检查是否已评价

### 违约记录
- `GET /api/breaches` - 我的违约记录

### 管理员接口
- `GET /api/admin/users` - 用户列表
- `GET /api/admin/users/{id}` - 用户详情
- `POST /api/admin/users` - 创建用户
- `PUT /api/admin/users/{id}` - 更新用户
- `DELETE /api/admin/users/{id}` - 删除用户
- `GET /api/admin/bids` - 所有出价列表
- `DELETE /api/admin/bids/{id}` - 取消出价

## 开发说明

### 前端开发

- 前端使用 Vite 作为构建工具，支持热更新
- API 请求会自动代理到后端 (配置在 `vite.config.js`)
- 状态管理使用 Pinia
- 路由使用 Vue Router 4

### 后端开发

- 使用 Spring Security 进行权限控制
- 使用 JWT 进行无状态认证
- 使用 MyBatis Plus 简化数据库操作
- 支持文件上传存储

## 测试账号

- 用户名: `testuser`
- 密码: `password123`

- 管理员: `admin`
- 密码: `password123`

## 常见问题

### 1. 跨域问题
后端已配置 CORS，允许来自 `http://localhost:3000` 的请求。

### 2. 数据库连接失败
检查 MySQL 服务是否启动，以及 `application.properties` 中的配置是否正确。

### 3. 前端无法连接后端
确保后端已启动在 8080 端口，前端配置的代理地址正确。

