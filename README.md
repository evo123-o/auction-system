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

## 系统功能模块图

详细功能模块图见 [SYSTEM_FUNCTION_MODULE_DIAGRAM.md](SYSTEM_FUNCTION_MODULE_DIAGRAM.md)。

## 实体关系图

详细实体关系图见 [ENTITY_RELATIONSHIP_DIAGRAM.md](ENTITY_RELATIONSHIP_DIAGRAM.md)。

## 快速开始

### 1. 环境要求

- Java 17+
- Node.js 20+
- MySQL 8+
- Redis (可选，用于缓存和 token 黑名单)
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
spring.datasource.password=your_password
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

2. **拍品管理**
   - 浏览拍品列表
   - 查看拍品详情
   - 创建新拍品
   - 编辑拍品信息
   - 删除拍品
   - 上传拍品图片

3. **竞拍功能**
   - 对拍品出价
   - 查看出价历史
   - 实时价格更新

4. **用户管理**
   - 个人中心/仪表盘
   - 查看我的拍品

## API 端点

### 认证相关
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户登出
- `POST /api/auth/refresh` - 刷新 token

### 拍品相关
- `GET /api/items` - 获取拍品列表
- `GET /api/items/{id}` - 获取拍品详情
- `POST /api/items` - 创建拍品
- `PUT /api/items/{id}` - 更新拍品
- `DELETE /api/items/{id}` - 删除拍品
- `POST /api/items/{id}/image` - 上传拍品图片

### 竞拍相关
- `POST /api/bids/place` - 出价
- `POST /api/items/{id}/bid` - 对指定拍品出价
- `GET /api/bids/history` - 查看出价历史

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

## 许可证

本项目仅用于学习和演示目的。
