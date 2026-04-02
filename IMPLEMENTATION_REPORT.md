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

#### 核心代码片段

1. **用户认证控制器 (AuthController)**

```java
// src/main/java/org/example/auction/controller/AuthController.java
@PostMapping("/login")
public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
    try {
        // 1. 调用 AuthenticationManager 进行用户名密码认证
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(req.getUsername(), req.getPassword()));

        // 2. 生成 AccessToken
        String accessToken = jwtTokenUtil.generateToken(req.getUsername());

        // 3. 获取用户信息并生成 RefreshToken
        User u = userService.findByUsername(req.getUsername());
        Long userId = u != null ? u.getId() : null;
        RefreshToken rt = refreshTokenService.createRefreshToken(userId);

        // 4. 返回包含 Token 和用户信息的响应
        return getResponseEntity(accessToken, u, rt);
    } catch (AuthenticationException ex) {
        return ResponseEntity.status(401).body(ApiResponse.fail("用户名或密码错误"));
    }
}

@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterReq req) {
    try {
        // 调用 Service 层处理注册逻辑
        User u = userService.register(req.getUsername(), req.getPassword(), req.getEmail());
        return ResponseEntity.ok(ApiResponse.ok(u.getUsername()));
    } catch (IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(ex.getMessage()));
    }
}
```

2. **用户服务逻辑 (UserServiceImpl)**

```java
// src/main/java/org/example/auction/service/impl/UserServiceImpl.java
@Override
@Transactional(rollbackFor = Exception.class)
public User register(String username, String rawPassword, String email) {
    // 1. 唯一性校验
    if (existsByUsername(username)) throw new IllegalArgumentException("用户名已存在");
    if (email != null && existsByEmail(email)) throw new IllegalArgumentException("邮箱已被使用");

    // 2. 创建用户实体并设置初始状态
    User u = new User();
    u.setUsername(username);
    u.setPassword(encoder.encode(rawPassword)); // BCrypt加密
    u.setEmail(email);
    u.setRole("USER");
    u.setCreditScore(100); // 初始信用分
    u.setStatus("ACTIVE");
    u.setCreatedAt(LocalDateTime.now());

    // 3. 持久化到数据库
    userMapper.insert(u);
    return u;
}
```

3. **安全配置 (SecurityConfig)**

```java
// src/main/java/org/example/auction/config/SecurityConfig.java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenUtil, userDetailsService, jwtProperties, tokenBlacklistService);

    http
        .cors(Customizer.withDefaults())
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态会话
        .authorizeHttpRequests(auth -> auth
            // 放行公开接口
            .requestMatchers("/", "/index.html", "/error").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/uploads/**", "/receipts/**", "/static/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            // 其他接口需认证
            .anyRequest().authenticated()
        )
        .authenticationManager(authenticationManager);

    // 添加 JWT 过滤器
    http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

### 2.2 拍品模块实现（发布、查询、状态更新）

- **发布/更新**：`ItemController#create` 与 `ItemServiceImpl#create` 负责创建拍品并初始化状态；更新时支持审核逻辑与状态重置。
- **查询**：`ItemController#list` 支持分页与条件筛选，`detail` 获取单个拍品详情。
- **状态更新**：`ItemStatusScheduler` 定时将已到开始/结束时间的拍品状态更新为 `RUNNING/CLOSED`。
- **实时状态检查**：`ItemServiceImpl#getById` 在查询时实时检查，如果拍品已上架且当前时间处于开始和结束时间之间，自动将状态更新为 `RUNNING`。

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

// 实时状态检查：查询时自动更新状态
@Override
public Item getById(Long id) {
    Item item = itemMapper.selectById(id);
    if (item != null && "ON_SHELF".equalsIgnoreCase(item.getStatus())) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(item.getStartTime()) && now.isBefore(item.getEndTime())) {
            item.setStatus("RUNNING");
            itemMapper.updateById(item);
        }
    }
    return item;
}

// 停止拍卖时设置结束时间，确保倒计时立即结束
@Override
@Transactional
public Item stopAuction(Long id) {
    Item item = itemMapper.selectById(id);
    item.setStatus("CLOSED");
    item.setEndTime(LocalDateTime.now()); // 立即结束倒计时
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

### 2.4 订单与支付模块实现（订单创建、状态流转、支付宝沙箱支付）

- **订单创建**：`EndAuctionScheduler` 扫描结束的拍卖并选出最高出价者，调用 `OrderServiceImpl#createOrderFromWinningBid` 创建订单。
- **状态流转**：`OrderServiceImpl` 提供 `markPaid / markShipped / markReceived` 等状态变更方法；`OrderController` 对权限进行校验。
- **支付宝沙箱支付**：集成支付宝沙箱环境，支持订单支付和保证金支付，包含支付表单生成、异步通知处理和同步回调跳转。

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

#### 支付宝沙箱支付集成

**1. 支付宝配置类 (AlipayProperties)**

```java
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {
    private String appId;
    private String merchantPrivateKey;
    private String alipayPublicKey;
    private String gatewayUrl;
    private String notifyUrl;   // 异步通知地址
    private String returnUrl;   // 同步回调地址
    // getters and setters...
}
```

**2. 支付服务接口 (PaymentService)**

```java
// src/main/java/org/example/auction/service/PaymentService.java
public interface PaymentService {
    Map<String, String> createOrderPayInfo(Order order);
    Map<String, String> createDepositPayInfo(Deposit deposit);
    boolean handleAlipayNotify(Map<String, String> params);
}
```

**3. 支付宝支付服务实现 (AlipayPaymentServiceImpl)**

```java
@Service
public class AlipayPaymentServiceImpl implements PaymentService {

    // 创建订单支付信息
    @Override
    public Map<String, String> createOrderPayInfo(Order order) {
        String outTradeNo = "ORDER_" + order.getId() + "_" + System.currentTimeMillis();
        String payForm = buildPagePayForm(outTradeNo, order.getFinalPrice(), "拍卖订单支付");

        Map<String, String> result = new HashMap<>();
        result.put("payForm", payForm);
        result.put("outTradeNo", outTradeNo);
        return result;
    }

    // 处理支付宝异步通知
    @Override
    public boolean handleAlipayNotify(Map<String, String> params) {
        // 验证签名
        boolean verified = AlipaySignature.rsaCheckV1(params,
            alipayProperties.getAlipayPublicKey(),
            alipayProperties.getCharset(),
            alipayProperties.getSignType());
        if (!verified) return false;

        String outTradeNo = params.get("out_trade_no");
        // 根据前缀区分业务类型
        if (outTradeNo.startsWith("ORDER_")) {
            orderService.markPaid(parseId(outTradeNo));
        } else if (outTradeNo.startsWith("DEPOSIT_")) {
            depositService.markPaid(parseId(outTradeNo), params.get("trade_no"));
        }
        return true;
    }

    // 构建支付宝支付表单
    private String buildPagePayForm(String outTradeNo, BigDecimal amount, String subject) {
        AlipayClient client = new DefaultAlipayClient(
            alipayProperties.getGatewayUrl(),
            alipayProperties.getAppId(),
            alipayProperties.getMerchantPrivateKey(),
            "json", "utf-8",
            alipayProperties.getAlipayPublicKey(),
            "RSA2");

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setNotifyUrl(alipayProperties.getNotifyUrl());
        request.setReturnUrl(alipayProperties.getReturnUrl());
        request.setBizContent("{\"out_trade_no\":\"" + outTradeNo + "\",\"product_code\":\"FAST_INSTANT_TRADE_PAY\",\"total_amount\":\"" + amount + "\",\"subject\":\"" + subject + "\"}");

        return client.pageExecute(request).getBody();
    }
}
```

**4. 支付宝回调控制器 (AlipayController)**

```java
@RestController
@RequestMapping("/alipay")
public class AlipayController {

    private final PaymentService paymentService;

    // 异步通知回调（支付宝服务器调用）
    @PostMapping("/notify")
    public String notifyCallback(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v[0]));
        return paymentService.handleAlipayNotify(params) ? "success" : "failure";
    }

    // 同步回调跳转（支付完成后跳转回前端）
    @GetMapping("/return")
    public ResponseEntity<Void> returnCallback(@RequestParam String out_trade_no,
                                                @RequestParam String trade_status) {
        String target = frontendBaseUrl + "/?payResult=" + trade_status + "&outTradeNo=" + out_trade_no;
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}
```

**5. 订单控制器支付接口 (OrderController)**

```java
@PostMapping("/pay/{id}")
public ResponseEntity<?> pay(@PathVariable Long id) {
    Order order = orderService.getById(id);
    // 权限校验...
    Map<String, String> payInfo = paymentService.createOrderPayInfo(order);
    return ResponseEntity.ok(ApiResponse.ok(payInfo)); // 返回支付表单HTML
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

| 页面     | 说明                                          |
| -------- | --------------------------------------------- |
| 首页     | ![首页](docs/screenshots/home.svg)            |
| 拍品列表 | ![拍品列表](docs/screenshots/items-list.svg)  |
| 拍品详情 | ![拍品详情](docs/screenshots/item-detail.svg) |
| 个人中心 | ![个人中心](docs/screenshots/profile.svg)     |
| 后台管理 | ![后台管理](docs/screenshots/admin.svg)       |

## 3. 关键技术难点与解决方案

1. **高并发竞拍数据一致性**
   - 难点：多用户同时出价会导致"最高价覆盖"或"脏写"。
   - 解决方案：在 `BidServiceImpl#placeBid` 中使用 `FOR UPDATE` 对拍品记录加锁，并在事务内完成出价与价格更新，保证一致性。

2. **支付宝沙箱支付集成**
   - 难点：需要对接支付宝沙箱环境，处理支付表单生成、异步通知验证和同步回调跳转。
   - 解决方案：
     - 使用支付宝官方SDK创建支付客户端，生成PC端支付表单。
     - 配置异步通知地址（notifyUrl）和同步回调地址（returnUrl），通过NATAPP内网穿透实现本地开发环境接收回调。
     - 在 `AlipayPaymentServiceImpl#handleAlipayNotify` 中使用 `AlipaySignature.rsaCheckV1` 验证回调签名，确保通知来源可信。
     - 支持订单支付和保证金支付两种业务类型，通过订单号前缀（ORDER*/DEPOSIT*）区分处理逻辑。

3. **支付异步通知与幂等处理**
   - 难点：第三方支付回调可能重复触发，导致订单状态异常。
   - 解决方案：`OrderServiceImpl#markPaid` 先判断当前状态，确保状态变更幂等；支付宝通知处理中仅处理 `TRADE_SUCCESS` 和 `TRADE_FINISHED` 状态，避免重复处理。

4. **拍卖状态实时同步**
   - 难点：定时任务更新状态存在延迟，用户查询时可能看到旧状态。
   - 解决方案：在 `ItemServiceImpl#getById` 中增加实时状态检查逻辑，查询时如果拍品已上架且当前时间在拍卖时间段内，立即更新状态为 `RUNNING`，确保用户看到最新状态。

5. **拍卖结束与定时任务**
   - 难点：需要自动结束拍卖、生成订单、处理保证金与违约。
   - 解决方案：
     - `ItemStatusScheduler` 周期性更新拍品状态。
     - `EndAuctionScheduler` 扫描已结束拍品并创建订单、退款/冻结保证金、通知用户。
     - `OverdueOrderScheduler` 处理未按时支付的订单并执行违约逻辑。
