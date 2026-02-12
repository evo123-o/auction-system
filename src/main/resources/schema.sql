-- sql
-- Light-weight Auction System schema for MySQL 8+
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop in reverse order to avoid FK conflicts when re-running
DROP TABLE IF EXISTS breach_records;
DROP TABLE IF EXISTS violations;
DROP TABLE IF EXISTS evaluations;
DROP TABLE IF EXISTS logistics;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS deposits;
DROP TABLE IF EXISTS bids;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;

SET FOREIGN_KEY_CHECKS = 1;

-- users: 平台用户（管理员/竞拍者）
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(64) NOT NULL UNIQUE,
                                     password VARCHAR(256) NOT NULL, -- 存储 BCrypt 哈希
                                     email VARCHAR(128),
                                     role VARCHAR(32) NOT NULL DEFAULT 'USER', -- ADMIN / USER
                                     credit_score INT NOT NULL DEFAULT 100,
                                     status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / BANNED / DISABLED
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- refresh_tokens: JWT refresh token 存储
CREATE TABLE IF NOT EXISTS refresh_tokens (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              token VARCHAR(128) NOT NULL UNIQUE,
                                              expires_at DATETIME NOT NULL,
                                              revoked BOOLEAN NOT NULL DEFAULT FALSE,
                                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- items: 拍品信息
CREATE TABLE IF NOT EXISTS items (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     title VARCHAR(255) NOT NULL,
                                     category VARCHAR(64),
                                     description TEXT,
                                     start_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                                     current_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                                     deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                                     start_time DATETIME,
                                     end_time DATETIME,
                                     status VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING / ON_SHELF / RUNNING / SOLD / CLOSED
                                     extend_count INT NOT NULL DEFAULT 0,
                                     max_extend INT NOT NULL DEFAULT 3,
                                     image_path VARCHAR(512),
                                     reject_reason VARCHAR(255),
                                     created_by BIGINT,
                                     created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                     auto_extension TINYINT(1) NOT NULL DEFAULT 0,
                                     CONSTRAINT fk_items_creator FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_items_status ON items(status);
CREATE INDEX idx_items_end_time ON items(end_time);

-- bids: 出价记录
CREATE TABLE IF NOT EXISTS bids (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    item_id BIGINT NOT NULL,
                                    user_id BIGINT NOT NULL,
                                    amount DECIMAL(12,2) NOT NULL,
                                    bid_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_bids_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_bids_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_bids_item ON bids(item_id);
CREATE INDEX idx_bids_user ON bids(user_id);

-- deposits: 保证金缴纳记录（保留单一定义）
CREATE TABLE IF NOT EXISTS deposits (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        item_id BIGINT NULL,
                                        amount DECIMAL(12,2) NOT NULL,
                                        status VARCHAR(32) NOT NULL DEFAULT 'PAID', -- UNPAID / PAID / FROZEN / REFUNDED / FORFEITED
                                        payment_ref VARCHAR(255),
                                        paid_at DATETIME,
                                        frozen_at DATETIME,
                                        refunded_at DATETIME,
                                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        updated_at TIMESTAMP NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_deposits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                        CONSTRAINT fk_deposits_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_deposits_user ON deposits(user_id);
CREATE INDEX idx_deposits_item ON deposits(item_id);

-- orders: 成交订单（保留单一定义）
CREATE TABLE IF NOT EXISTS orders (
                                      id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                      item_id BIGINT NOT NULL,
                                      buyer_id BIGINT NOT NULL,
                                      seller_id BIGINT,
                                      final_price DECIMAL(12,2) NOT NULL,
                                      status VARCHAR(32) NOT NULL DEFAULT 'AWAIT_PAY', -- AWAIT_PAY / PAID / SHIPPED / RECEIVED / CLOSED / CANCELLED
                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      paid_at DATETIME,
                                      shipped_at DATETIME,
                                      received_at DATETIME,
                                      closed_at DATETIME,
                                      pay_by DATETIME,
                                      receipt_path VARCHAR(255),
                                      CONSTRAINT fk_orders_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_buyer ON orders(buyer_id);

-- logistics: 物流信息
CREATE TABLE IF NOT EXISTS logistics (
                                         id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                         order_id BIGINT NOT NULL,
                                         company VARCHAR(128),
                                         tracking_no VARCHAR(128),
                                         updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                         notes VARCHAR(512),
                                         CONSTRAINT fk_logistics_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_logistics_order ON logistics(order_id);

-- evaluations: 交易评价
CREATE TABLE IF NOT EXISTS evaluations (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           order_id BIGINT NOT NULL,
                                           reviewer_id BIGINT NOT NULL,
                                           rating TINYINT NOT NULL DEFAULT 5, -- 1-5 星
                                           comment TEXT,
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           CONSTRAINT fk_evals_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
                                           CONSTRAINT fk_evals_user FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX idx_evals_order ON evaluations(order_id);
-- breach_records: 额外的违约/处罚记录（如果需要）
CREATE TABLE IF NOT EXISTS breach_records (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              item_id BIGINT NULL,
                                              order_id BIGINT NULL,
                                              reason VARCHAR(128) NOT NULL,
                                              penalty_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
                                              credit_score_delta INT NOT NULL DEFAULT 0,
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              INDEX idx_breach_user (user_id),
                                              CONSTRAINT fk_breach_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                                              CONSTRAINT fk_breach_item FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE SET NULL,
                                              CONSTRAINT fk_breach_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET FOREIGN_KEY_CHECKS = 1;
