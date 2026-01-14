-- 简化的建表脚本，开发阶段可先用
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    role VARCHAR(16) DEFAULT 'USER',
    credit_score INT DEFAULT 100,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS items (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     title VARCHAR(255) NOT NULL,
    category VARCHAR(64),
    description TEXT,
    start_price DECIMAL(12,2) DEFAULT 0,
    current_price DECIMAL(12,2) DEFAULT 0,
    deposit_amount DECIMAL(12,2) DEFAULT 0,
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(32) DEFAULT 'PENDING',
    extend_count INT DEFAULT 0,
    max_extend INT DEFAULT 3,
    image_path VARCHAR(512),
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

CREATE TABLE IF NOT EXISTS bids (
                                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                    item_id BIGINT NOT NULL,
                                    user_id BIGINT NOT NULL,
                                    amount DECIMAL(12,2) NOT NULL,
    bid_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_item (item_id)
    );

CREATE TABLE IF NOT EXISTS deposits (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        item_id BIGINT,
                                        amount DECIMAL(12,2) NOT NULL,
    status VARCHAR(32) DEFAULT 'PAID',
    paid_at DATETIME,
    frozen_at DATETIME,
    refunded_at DATETIME
    );