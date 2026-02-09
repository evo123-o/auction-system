# 拍卖系统实现说明

## 1. 开发环境与工具

- **IDE**：IntelliJ IDEA（后端开发）、VS Code（前端/文档编辑）
- **数据库与缓存工具**：MySQL Workbench / DataGrip（数据库管理）、RedisInsight（Redis 可视化）
- **接口调试**：Swagger UI（后端已集成）、Postman / Apifox
- **构建与依赖管理**：Maven（Spring Boot）、Node.js + npm（前端构建）
- **版本控制**：Git + GitHub（PR/Issue 工作流）

## 2. 核心功能模块实现

### 2.1 用户模块实现（注册、登录、权限校验）

- **注册逻辑**：`AuthController#register` 调用 `UserServiceImpl#register`，先校验用户名/邮箱唯一性，再使用 BCrypt 加密密码并写入数据库。
- **登录逻辑**：`AuthController#login` 使用 Spring Security 的 `AuthenticationManager` 完成认证，生成 JWT + Refresh Token。
- **权限校验**：`SecurityConfig` 统一配置 JWT 过滤器与开放/受保护的 API 路径，实现对接口的访问控制。

代码示例（注册/登录）：

```java
// src/main/java/org/example/auction/controller/AuthController.java
@PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));
    String accessToken = jwtTokenUtil.generateToken(req.getUsername());
    User u = userService.findByUsername(req.getUsername());
    Long userId = u != null ? u.getId() : null;
    RefreshToken rt = refreshTokenService.createRefreshToken(userId);
    return ResponseEntity.ok(ApiResponse.ok(Map.of(
        "accessToken", jwtProperties.getTokenPrefix() + accessToken,
        "refreshToken", rt.getToken())));
}

@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterReq req) {
    User u = userService.register(req.getUsername(), req.getPassword(), req.getEmail());
    return ResponseEntity.ok(ApiResponse.ok(u.getUsername()));
}
```

```java
// src/main/java/org/example/auction/service/impl/UserServiceImpl.java
public User register(String username, String rawPassword, String email) {
    if (existsByUsername(username)) throw new IllegalArgumentException("用户名已存在");
    if (email != null && existsByEmail(email)) throw new IllegalArgumentException("邮箱已被使用");
    User u = new User();
    u.setPassword(encoder.encode(rawPassword));
    u.setRole("USER");
    userMapper.insert(u);
    return u;
}
```

### 2.2 拍品模块实现（发布、查询、状态更新）

- **发布/更新**：`ItemController#create` 与 `ItemServiceImpl#create` 负责创建拍品并初始化状态；更新时支持审核逻辑与状态重置。
- **查询**：`ItemController#list` 支持分页与条件筛选，`detail` 获取单个拍品详情。
- **状态更新**：`ItemStatusScheduler` 定时将已到开始/结束时间的拍品状态更新为 `RUNNING/CLOSED`。

代码示例（创建与状态更新）：

```java
// src/main/java/org/example/auction/service/impl/ItemServiceImpl.java
public Item create(CreateItemRequest req, Long createdBy) {
    Item item = new Item();
    BeanUtils.copyProperties(req, item);
    item.setCreatedBy(createdBy);
    item.setStatus("PENDING");
    itemMapper.insert(item);
    item.setCurrentPrice(item.getStartPrice());
    itemMapper.updateById(item);
    return item;
}
```

```java
// src/main/java/org/example/auction/schedule/ItemStatusScheduler.java
@Scheduled(fixedDelay = 30_000)
public void flipStatuses() {
    itemMapper.update(null, new LambdaUpdateWrapper<Item>()
        .ne(Item::getStatus, "RUNNING")
        .le(Item::getStartTime, now)
        .gt(Item::getEndTime, now)
        .set(Item::getStatus, "RUNNING"));
}
```

### 2.3 竞拍模块实现（出价逻辑、时间控制、价格更新）

- **出价逻辑**：`BidServiceImpl#placeBid` 使用 `FOR UPDATE` 对拍品行加锁，防止并发写入冲突。
- **时间控制**：检查拍卖开始/结束时间，并支持自动延时策略（结束前 N 分钟内出价自动延长）。
- **价格更新**：出价成功后更新拍品 `current_price`，确保展示的最新价格一致。

代码示例（核心出价逻辑）：

```java
// src/main/java/org/example/auction/service/impl/BidServiceImpl.java
Item item = itemMapper.selectOne(new LambdaQueryWrapper<Item>()
    .eq(Item::getId, itemId)
    .last("FOR UPDATE"));

if (amount.compareTo(highestBid.getAmount()) <= 0) {
    throw new IllegalArgumentException("出价必须高于当前最高价");
}

bidMapper.insert(bid);
itemMapper.update(null, updateWrapper
    .set(Item::getCurrentPrice, amount)
    .set(Item::getUpdatedAt, LocalDateTime.now()));
```

### 2.4 订单与支付模块实现（订单创建、状态流转、支付回调处理）

- **订单创建**：`EndAuctionScheduler` 扫描结束的拍卖并选出最高出价者，调用 `OrderServiceImpl#createOrderFromWinningBid` 创建订单。
- **状态流转**：`OrderServiceImpl` 提供 `markPaid / markShipped / markReceived` 等状态变更方法；`OrderController` 对权限进行校验。
- **支付回调处理**：当前实现为“模拟支付”接口（`/api/orders/pay/{id}`）；真实支付可在此处对接回调并保持幂等处理。

代码示例（订单创建与支付状态变更）：

```java
// src/main/java/org/example/auction/service/impl/OrderServiceImpl.java
public Order createOrderFromWinningBid(Item item, Bid winnerBid) {
    Order order = Order.builder()
        .itemId(item.getId())
        .buyerId(winnerBid.getUserId())
        .status("PENDING_PAYMENT")
        .payBy(LocalDateTime.now().plusHours(payDeadlineHours))
        .build();
    orderMapper.insert(order);
    return order;
}

public Order markPaid(Long id) {
    Order order = orderMapper.selectById(id);
    if (!"PAID".equalsIgnoreCase(order.getStatus())) {
        order.setStatus("PAID");
        orderMapper.updateById(order);
    }
    return order;
}
```

### 2.5 前端页面实现（主要页面效果图与交互）

> 当前仓库仅包含后端实现，以下为与 API 对应的前端页面交互设计示意（可用于前端实现或答辩说明）。

- **首页**：展示推荐拍品与公告信息，调用 `GET /api/items` 获取拍品列表。
- **拍品列表**：支持分类/状态筛选与分页，调用 `GET /api/items` 携带查询参数。
- **拍品详情页**：展示拍品信息与出价历史，调用 `GET /api/items/{id}` 与 `GET /api/bids/history`。
- **个人中心**：展示我的拍品、我的订单，调用 `GET /api/orders/my/buyer`、`GET /api/orders/my/seller` 等接口。
- **后台管理**：管理员审核拍品/管理订单/管理用户，调用 `ItemAdminController`、`AdminUserController` 等接口。

效果图（线框示意）：

| 页面 | 说明 |
| --- | --- |
| 首页 | ![首页](docs/screenshots/home.svg) |
| 拍品列表 | ![拍品列表](docs/screenshots/items-list.svg) |
| 拍品详情 | ![拍品详情](docs/screenshots/item-detail.svg) |
| 个人中心 | ![个人中心](docs/screenshots/profile.svg) |
| 后台管理 | ![后台管理](docs/screenshots/admin.svg) |

## 3. 关键技术难点与解决方案

1. **高并发竞拍数据一致性**
   - 难点：多用户同时出价会导致“最高价覆盖”或“脏写”。
   - 解决方案：在 `BidServiceImpl#placeBid` 中使用 `FOR UPDATE` 对拍品记录加锁，并在事务内完成出价与价格更新，保证一致性。

2. **支付异步通知与幂等处理**
   - 难点：第三方支付回调可能重复触发，导致订单状态异常。
   - 解决方案：`OrderServiceImpl#markPaid` 先判断当前状态，确保状态变更幂等；可在此基础上扩展真实支付回调逻辑。

3. **拍卖结束与定时任务**
   - 难点：需要自动结束拍卖、生成订单、处理保证金与违约。
   - 解决方案：
     - `ItemStatusScheduler` 周期性更新拍品状态。
     - `EndAuctionScheduler` 扫描已结束拍品并创建订单、退款/冻结保证金、通知用户。
     - `OverdueOrderScheduler` 处理未按时支付的订单并执行违约逻辑。

## 4. 关键技术说明

### 4.1 Spring Boot 框架

Spring Boot 是基于 Spring 生态的快速开发框架，核心特性包括自动配置、起步依赖（Starter）以及内嵌服务器。自动配置让常见组件开箱即用，起步依赖减少了手动维护依赖版本的成本，内嵌的 Tomcat 让应用可以直接以 Jar 方式运行。在本系统中，Spring Boot 负责统一配置、依赖管理与运行环境，显著提升后端开发效率。

### 4.2 Vue 3 前端框架

Vue 3 的核心能力包含响应式系统、组件化开发方式以及 Composition API。响应式系统通过 Proxy 追踪数据变化，组件化让页面拆分为可复用模块，Composition API 便于组织复杂业务逻辑。本系统前端采用 Vue 3 构建页面交互，配合路由与状态管理实现拍品展示、出价与个人中心等功能。

### 4.3 MySQL 数据库

MySQL 是成熟的关系型数据库，支持标准 SQL、事务与索引优化，适合结构化数据存储。关系型模型以表、行、列描述业务实体及其关联。本系统使用 MySQL 保存用户、拍品、竞拍记录、订单等核心业务数据，并通过外键关联保持数据一致性。

### 4.4 B/S 架构

B/S（Browser/Server）架构以浏览器为客户端，通过网络访问服务器端服务与数据层，通常由浏览器、应用服务器与数据库三部分组成。其优势是部署与更新集中在服务器端，客户端无需安装，跨平台成本低。本系统采用 B/S 架构，用户通过浏览器访问前端页面并调用后端服务。

### 4.5 前后端分离架构

前后端分离指前端与后端独立开发、部署，通过标准接口进行通信。优势包括职责清晰、易于维护、可扩展性强。系统通过 RESTful API 进行数据交互，前端使用 HTTP/HTTPS 请求后端接口，后端返回统一的 JSON 响应。

### 4.6 其他关键技术

- **MyBatis Plus**：提供 Mapper 与 CRUD 快速开发能力，简化数据访问层代码。
- **Element Plus**：用于前端 UI 组件库，提升页面一致性与开发效率。
- **JWT 鉴权**：登录后签发 Token，通过 `Authorization` 头完成无状态认证。
- **Redis 缓存**：用于缓存热点数据与维护 Token 黑名单，提升访问性能。
- **Swagger/OpenAPI**：用于生成接口文档并辅助接口调试。
