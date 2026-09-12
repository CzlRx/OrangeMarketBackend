-- OrangeMarket MySQL 初始化脚本（Docker 专用）
-- 来源：OrangeMarketBackend/sql/
--   - create_core_business_tables.sql
--   - insert_demo_data.sql
--   - insert_more_products.sql
--
-- 用法（MySQL 官方镜像）：
--   volumes:
--     - ./docker/mysql/init:/docker-entrypoint-initdb.d:ro
--   environment:
--     MYSQL_DATABASE: orange_market_simple
--     MYSQL_ROOT_PASSWORD: 123456
--
-- 说明：
--   1. 仅在数据目录首次初始化时自动执行
--   2. 需要 MySQL 8.0+（utf8mb4_0900_ai_ci）
--   3. 建表使用 IF NOT EXISTS，演示数据使用 INSERT IGNORE，可重复执行更安全

CREATE DATABASE IF NOT EXISTS `orange_market_simple`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `orange_market_simple`;

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user_account` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `phone` VARCHAR(20) NOT NULL,
    `nickname` VARCHAR(64) NOT NULL,
    `avatar_url` VARCHAR(512) NULL,
    `gender` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `birthday` DATE NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'active',
    `role` VARCHAR(32) NOT NULL DEFAULT 'USER',
    `last_login_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_phone` (`phone`),
    KEY `idx_user_account_status` (`status`),
    KEY `idx_user_account_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `product_category` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `parent_id` BIGINT UNSIGNED NULL,
    `code` VARCHAR(64) NOT NULL,
    `name` VARCHAR(64) NOT NULL,
    `eyebrow` VARCHAR(128) NULL,
    `color` VARCHAR(16) NULL,
    `icon_key` VARCHAR(64) NULL,
    `is_virtual` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `sort_order` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'active',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_category_code` (`code`),
    KEY `idx_product_category_parent` (`parent_id`, `status`, `sort_order`),
    KEY `idx_product_category_deleted` (`deleted_at`),
    CONSTRAINT `fk_simple_product_category_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `category_id` BIGINT UNSIGNED NULL,
    `name` VARCHAR(255) NOT NULL,
    `subtitle` VARCHAR(255) NULL,
    `description` TEXT NULL,
    `cover_image` VARCHAR(512) NULL,
    `images_json` JSON NULL,
    `video_url` VARCHAR(512) NULL,
    `sale_price` DECIMAL(10,2) NOT NULL,
    `original_price` DECIMAL(10,2) NOT NULL,
    `shipping_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `stock` INT UNSIGNED NOT NULL DEFAULT 0,
    `sales_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `rating_avg` DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    `review_count` INT UNSIGNED NOT NULL DEFAULT 0,
    `tags_json` JSON NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft',
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_product_category_status_sort` (`category_id`, `status`, `sort_order`, `id`),
    KEY `idx_product_status_sales` (`status`, `sales_count`),
    KEY `idx_product_deleted` (`deleted_at`),
    FULLTEXT KEY `ft_product_search` (`name`, `subtitle`, `description`),
    CONSTRAINT `fk_simple_product_category`
        FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `cart_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `quantity` INT UNSIGNED NOT NULL DEFAULT 1,
    `selected` TINYINT UNSIGNED NOT NULL DEFAULT 1,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_item_user_product` (`user_id`, `product_id`),
    KEY `idx_cart_item_user_selected` (`user_id`, `selected`),
    CONSTRAINT `fk_simple_cart_item_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_cart_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_address` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `receiver` VARCHAR(64) NOT NULL,
    `phone` VARCHAR(20) NOT NULL,
    `province_code` VARCHAR(32) NULL,
    `province` VARCHAR(64) NOT NULL,
    `city_code` VARCHAR(32) NULL,
    `city` VARCHAR(64) NOT NULL,
    `district_code` VARCHAR(32) NULL,
    `district` VARCHAR(64) NOT NULL,
    `detail` VARCHAR(255) NOT NULL,
    `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_user_address_user_default` (`user_id`, `is_default`, `deleted_at`),
    CONSTRAINT `fk_simple_user_address_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_no` VARCHAR(32) NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending_payment',
    `subtotal_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `shipping_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `buyer_remark` VARCHAR(100) NULL,
    `receiver` VARCHAR(64) NOT NULL,
    `receiver_phone` VARCHAR(20) NOT NULL,
    `province` VARCHAR(64) NOT NULL,
    `city` VARCHAR(64) NOT NULL,
    `district` VARCHAR(64) NOT NULL,
    `detail` VARCHAR(255) NOT NULL,
    `payment_method` VARCHAR(20) NULL,
    `payment_expire_at` DATETIME(3) NULL,
    `paid_at` DATETIME(3) NULL,
    `shipped_at` DATETIME(3) NULL,
    `received_at` DATETIME(3) NULL,
    `completed_at` DATETIME(3) NULL,
    `cancelled_at` DATETIME(3) NULL,
    `cancel_reason` VARCHAR(255) NULL,
    `tracking_no` VARCHAR(128) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_user_status_time` (`user_id`, `status`, `created_at`),
    KEY `idx_orders_expire` (`status`, `payment_expire_at`),
    CONSTRAINT `fk_simple_orders_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT UNSIGNED NOT NULL,
    `product_id` BIGINT UNSIGNED NULL,
    `product_name` VARCHAR(255) NOT NULL,
    `product_image` VARCHAR(512) NULL,
    `unit_price` DECIMAL(10,2) NOT NULL,
    `quantity` INT UNSIGNED NOT NULL,
    `line_amount` DECIMAL(10,2) NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_product_time` (`product_id`, `created_at`),
    CONSTRAINT `fk_simple_order_item_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_order_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `product_review` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `order_item_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `rating` TINYINT UNSIGNED NOT NULL,
    `content` TEXT NOT NULL,
    `anonymous` TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `status` VARCHAR(20) NOT NULL DEFAULT 'visible',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_review_user_item` (`user_id`, `order_item_id`),
    KEY `idx_product_review_product_status_time` (`product_id`, `status`, `created_at`),
    CONSTRAINT `fk_simple_product_review_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_product_review_order_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_product_review_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_favorite_user_product` (`user_id`, `product_id`),
    KEY `idx_user_favorite_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_simple_user_favorite_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_user_favorite_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_browse_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `viewed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_browse_history_user_product` (`user_id`, `product_id`),
    KEY `idx_user_browse_history_user_time` (`user_id`, `viewed_at`),
    CONSTRAINT `fk_simple_user_browse_history_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_simple_user_browse_history_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS `user_search_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `keyword` VARCHAR(128) NOT NULL,
    `searched_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_search_history_user_keyword` (`user_id`, `keyword`),
    KEY `idx_user_search_history_user_time` (`user_id`, `searched_at`),
    CONSTRAINT `fk_simple_user_search_history_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO `product_category`
    (`code`, `name`, `eyebrow`, `color`, `icon_key`, `is_virtual`, `sort_order`, `status`)
VALUES
    ('digital', '数码', 'SMART LIFE', '#e7f5ff', 'Laptop', 0, 10, 'active'),
    ('fashion', '服饰', 'NEW SEASON', '#fff0f3', 'Shirt', 0, 20, 'active'),
    ('beauty', '美妆', 'GLOW UP', '#fff2e9', 'Sparkles', 0, 30, 'active'),
    ('food', '食品', 'TASTY DAY', '#fff7db', 'Cookie', 0, 40, 'active'),
    ('home', '家居', 'COZY HOME', '#ecf8ec', 'LampDesk', 0, 50, 'active'),
    ('sport', '运动', 'MOVE MORE', '#eaf4ff', 'Dumbbell', 0, 60, 'active');

-- ============================================================
-- 演示数据（来自 insert_demo_data.sql）
-- ============================================================

START TRANSACTION;

INSERT IGNORE INTO `user_account`
    (`id`, `phone`, `nickname`, `avatar_url`, `gender`, `birthday`, `status`, `role`)
VALUES
    (10001, '13800138001', '橙子同学', 'https://i.pravatar.cc/160?img=12', 0, '1998-08-18', 'active', 'USER'),
    (10002, '13800138002', '小橙子', 'https://i.pravatar.cc/160?img=32', 2, '2000-03-12', 'active', 'USER'),
    (10003, '13800138003', '阳光用户', NULL, 1, NULL, 'active', 'USER'),
    -- 预置管理员账号：用该手机号首次登录即直接是 ADMIN（不会再触发自动注册）
    (10004, '19091432641', '橙子用户2641', NULL, 0, NULL, 'active', 'ADMIN');

INSERT IGNORE INTO `product_category`
    (`code`, `name`, `eyebrow`, `color`, `icon_key`, `is_virtual`, `sort_order`, `status`)
VALUES
    ('digital', '数码', 'SMART LIFE', '#e7f5ff', 'Laptop', 0, 10, 'active'),
    ('fashion', '服饰', 'NEW SEASON', '#fff0f3', 'Shirt', 0, 20, 'active'),
    ('beauty', '美妆', 'GLOW UP', '#fff2e9', 'Sparkles', 0, 30, 'active'),
    ('food', '食品', 'TASTY DAY', '#fff7db', 'Cookie', 0, 40, 'active'),
    ('home', '家居', 'COZY HOME', '#ecf8ec', 'LampDesk', 0, 50, 'active'),
    ('sport', '运动', 'MOVE MORE', '#eaf4ff', 'Dumbbell', 0, 60, 'active');

SET @digital_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'digital' LIMIT 1);
SET @fashion_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'fashion' LIMIT 1);
SET @beauty_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'beauty' LIMIT 1);
SET @food_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'food' LIMIT 1);
SET @home_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'home' LIMIT 1);
SET @sport_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'sport' LIMIT 1);

INSERT IGNORE INTO `product`
    (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_image`, `images_json`, `video_url`,
     `sale_price`, `original_price`, `shipping_fee`, `stock`, `sales_count`, `rating_avg`, `review_count`,
     `tags_json`, `status`, `sort_order`)
VALUES
    (20001, @sport_category_id, '晨雾白跑鞋', '轻盈缓震，通勤与运动都舒适',
     '适合日常通勤和轻量运动的舒适跑鞋。',
     'https://picsum.photos/seed/orange-shoe/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-shoe/800/800', 'https://picsum.photos/seed/orange-shoe-2/800/800'),
     NULL, 239.00, 399.00, 8.00, 120, 8420, 4.90, 86,
     JSON_ARRAY('热卖', '舒适'), 'on_sale', 10),
    (20002, @digital_category_id, '橙光降噪耳机', '通勤降噪，长续航陪伴每段路',
     '支持主动降噪和环境音模式，适合通勤、学习和旅行。',
     'https://picsum.photos/seed/orange-headphone/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-headphone/800/800'),
     NULL, 329.00, 499.00, 0.00, 65, 5260, 4.70, 53,
     JSON_ARRAY('新品', '降噪'), 'on_sale', 20),
    (20003, @beauty_category_id, '柚香保湿面霜', '清爽不黏，换季也能保持水润',
     '清爽型保湿面霜，适合日常基础护肤。',
     'https://picsum.photos/seed/orange-cream/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-cream/800/800'),
     NULL, 89.00, 129.00, 6.00, 240, 3180, 4.60, 31,
     JSON_ARRAY('保湿', '清爽'), 'on_sale', 30),
    (20004, @food_category_id, '手作橙香曲奇', '酥脆香甜，下午茶的轻松选择',
     '独立包装手作曲奇，适合下午茶和分享。',
     'https://picsum.photos/seed/orange-cookie/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-cookie/800/800'),
     NULL, 39.90, 59.90, 5.00, 300, 2140, 4.80, 18,
     JSON_ARRAY('下午茶', '手作'), 'on_sale', 40),
    (20005, @home_category_id, '暖橙床头灯', '柔和光线，营造安静睡前氛围',
     '简约床头灯，支持三档亮度调节。',
     'https://picsum.photos/seed/orange-lamp/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-lamp/800/800'),
     NULL, 119.00, 169.00, 10.00, 48, 970, 4.50, 12,
     JSON_ARRAY('家居', '氛围感'), 'on_sale', 50),
    (20006, @fashion_category_id, '橙意帆布包', '轻便耐用，日常出门随手装',
     '容量适中的帆布托特包，适合日常出行。',
     'https://picsum.photos/seed/orange-bag/800/800',
     JSON_ARRAY('https://picsum.photos/seed/orange-bag/800/800'),
     NULL, 69.00, 99.00, 6.00, 90, 1560, 4.40, 15,
     JSON_ARRAY('百搭', '轻便'), 'on_sale', 60);

INSERT IGNORE INTO `cart_item`
    (`id`, `user_id`, `product_id`, `quantity`, `selected`)
VALUES
    (40001, 10001, 20001, 1, 1),
    (40002, 10001, 20002, 2, 1),
    (40003, 10002, 20004, 3, 0);

INSERT IGNORE INTO `user_address`
    (`id`, `user_id`, `receiver`, `phone`, `province_code`, `province`, `city_code`, `city`,
     `district_code`, `district`, `detail`, `is_default`)
VALUES
    (30001, 10001, '橙子同学', '13800138001', '310000', '上海市', '310100', '上海市',
     '310115', '浦东新区', '世纪大道 100 号橙子大厦 8 楼', 1),
    (30002, 10001, '橙子同学', '13800138001', '330000', '浙江省', '330100', '杭州市',
     '330106', '西湖区', '文三路 88 号', 0),
    (30003, 10002, '小橙子', '13800138002', '440000', '广东省', '440300', '深圳市',
     '440305', '南山区', '科技园南区 18 号', 1);

INSERT IGNORE INTO `orders`
    (`id`, `order_no`, `user_id`, `status`, `subtotal_amount`, `shipping_amount`, `total_amount`,
     `buyer_remark`, `receiver`, `receiver_phone`, `province`, `city`, `district`, `detail`,
     `payment_method`, `payment_expire_at`, `paid_at`, `shipped_at`, `received_at`, `completed_at`,
     `tracking_no`)
VALUES
    (60001, 'OM202609040001', 10001, 'pending_payment', 239.00, 8.00, 247.00,
     '请尽快发货', '橙子同学', '13800138001', '上海市', '上海市', '浦东新区', '世纪大道 100 号橙子大厦 8 楼',
     NULL, DATE_ADD(NOW(), INTERVAL 30 MINUTE), NULL, NULL, NULL, NULL, NULL),
    (60002, 'OM202609040002', 10001, 'pending_shipment', 658.00, 0.00, 658.00,
     NULL, '橙子同学', '13800138001', '浙江省', '杭州市', '西湖区', '文三路 88 号',
     'wechat', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 20 HOUR), NULL, NULL, NULL, NULL),
    (60003, 'OM202609040003', 10002, 'pending_review', 89.00, 6.00, 95.00,
     '工作日配送', '小橙子', '13800138002', '广东省', '深圳市', '南山区', '科技园南区 18 号',
     'alipay', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY),
     DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, 'SF1234567890');

INSERT IGNORE INTO `order_item`
    (`id`, `order_id`, `product_id`, `product_name`, `product_image`, `unit_price`, `quantity`, `line_amount`)
VALUES
    (61001, 60001, 20001, '晨雾白跑鞋', 'https://picsum.photos/seed/orange-shoe/800/800', 239.00, 1, 239.00),
    (61002, 60002, 20002, '橙光降噪耳机', 'https://picsum.photos/seed/orange-headphone/800/800', 329.00, 2, 658.00),
    (61003, 60003, 20003, '柚香保湿面霜', 'https://picsum.photos/seed/orange-cream/800/800', 89.00, 1, 89.00);

INSERT IGNORE INTO `product_review`
    (`id`, `product_id`, `order_item_id`, `user_id`, `rating`, `content`, `anonymous`, `status`)
VALUES
    (50001, 20001, 61001, 10001, 5, '鞋子很轻，脚感舒服，日常通勤很合适。', 0, 'visible'),
    (50002, 20002, 61002, 10001, 4, '降噪效果不错，续航也够日常使用。', 0, 'visible'),
    (50003, 20003, 61003, 10002, 5, '吸收很快，换季使用比较舒服。', 1, 'visible');

INSERT IGNORE INTO `user_favorite`
    (`id`, `user_id`, `product_id`)
VALUES
    (70001, 10001, 20003),
    (70002, 10001, 20005),
    (70003, 10002, 20001);

INSERT IGNORE INTO `user_browse_history`
    (`id`, `user_id`, `product_id`, `viewed_at`)
VALUES
    (80001, 10001, 20001, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
    (80002, 10001, 20003, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
    (80003, 10002, 20002, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (80004, 10003, 20006, DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT IGNORE INTO `user_search_history`
    (`id`, `user_id`, `keyword`, `searched_at`)
VALUES
    (90001, 10001, '跑鞋', DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
    (90002, 10001, '降噪耳机', DATE_SUB(NOW(), INTERVAL 1 HOUR)),
    (90003, 10001, '保湿', DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (90004, 10002, '家居', DATE_SUB(NOW(), INTERVAL 2 DAY));

COMMIT;

-- ============================================================
-- 补充商品数据（来自 insert_more_products.sql）
-- ============================================================

-- 补充更多商品假数据（可重复执行，依赖已有分类）
-- 用法：mysql -u root -p < sql/insert_more_products.sql
-- 依赖：create_core_business_tables.sql（含 6 个分类）
-- 说明：商品 ID 从 21001 起，不与 insert_demo_data.sql 的 20001-20006 冲突



START TRANSACTION;

SET @digital_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'digital' AND `deleted_at` = 0 LIMIT 1);
SET @fashion_category_id = (SELECT `id` FROM `product_category` WHERE `code` = 'fashion' AND `deleted_at` = 0 LIMIT 1);
SET @beauty_category_id  = (SELECT `id` FROM `product_category` WHERE `code` = 'beauty'  AND `deleted_at` = 0 LIMIT 1);
SET @food_category_id    = (SELECT `id` FROM `product_category` WHERE `code` = 'food'    AND `deleted_at` = 0 LIMIT 1);
SET @home_category_id    = (SELECT `id` FROM `product_category` WHERE `code` = 'home'    AND `deleted_at` = 0 LIMIT 1);
SET @sport_category_id   = (SELECT `id` FROM `product_category` WHERE `code` = 'sport'   AND `deleted_at` = 0 LIMIT 1);

INSERT IGNORE INTO `product`
    (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_image`, `images_json`, `video_url`,
     `sale_price`, `original_price`, `shipping_fee`, `stock`, `sales_count`, `rating_avg`, `review_count`,
     `tags_json`, `status`, `sort_order`)
VALUES
-- ========== 数码 digital ==========
(21001, @digital_category_id, '云橙无线鼠标', '静音按键，办公游戏两相宜',
 '2.4G 无线连接，续航约 12 个月，人体工学握感，适合长时间办公。',
 'https://picsum.photos/seed/om-mouse/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-mouse/800/800', 'https://picsum.photos/seed/om-mouse-2/800/800'),
 NULL, 79.00, 129.00, 0.00, 500, 3260, 4.60, 42,
 JSON_ARRAY('办公', '静音'), 'on_sale', 110),

(21002, @digital_category_id, '薄雾机械键盘', '青轴手感，RGB 灯效可自定义',
 '87 键紧凑布局，热插拔轴体，支持有线/蓝牙双模切换。',
 'https://picsum.photos/seed/om-keyboard/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-keyboard/800/800', 'https://picsum.photos/seed/om-keyboard-2/800/800'),
 NULL, 299.00, 459.00, 0.00, 180, 1890, 4.80, 67,
 JSON_ARRAY('热卖', '机械轴'), 'on_sale', 120),

(21003, @digital_category_id, '随身充电宝 20000mAh', '双向快充，出差旅行必备',
 '支持 PD 快充，双口输出，机身轻薄可放裤袋。',
 'https://picsum.photos/seed/om-powerbank/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-powerbank/800/800'),
 NULL, 129.00, 199.00, 0.00, 320, 5420, 4.50, 98,
 JSON_ARRAY('快充', '便携'), 'on_sale', 130),

(21004, @digital_category_id, '智能手环 Pro', '心率血氧监测，50 种运动模式',
 '1.47 寸高清屏，续航约 14 天，防水等级 5ATM。',
 'https://picsum.photos/seed/om-band/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-band/800/800', 'https://picsum.photos/seed/om-band-2/800/800'),
 NULL, 199.00, 299.00, 0.00, 210, 4120, 4.40, 55,
 JSON_ARRAY('健康', '运动'), 'on_sale', 140),

(21005, @digital_category_id, '超清摄像头 1080P', '直播网课居家办公都好用',
 '自动对焦，内置降噪麦克风，免驱即插即用。',
 'https://picsum.photos/seed/om-webcam/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-webcam/800/800'),
 NULL, 159.00, 249.00, 6.00, 95, 860, 4.30, 21,
 JSON_ARRAY('网课', '直播'), 'on_sale', 150),

(21006, @digital_category_id, '迷你蓝牙音箱', '口袋音质，户外也能响',
 'IPX7 防水，续航 12 小时，支持 TWS 双响配对。',
 'https://picsum.photos/seed/om-speaker/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-speaker/800/800'),
 NULL, 99.00, 159.00, 0.00, 260, 2780, 4.55, 39,
 JSON_ARRAY('防水', '便携'), 'on_sale', 160),

-- ========== 服饰 fashion ==========
(21011, @fashion_category_id, '橘意纯棉 T 恤', '柔软亲肤，夏季基础款',
 '100% 精梳棉，宽松版型，男女同款多色可选。',
 'https://picsum.photos/seed/om-tshirt/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-tshirt/800/800', 'https://picsum.photos/seed/om-tshirt-2/800/800'),
 NULL, 59.00, 99.00, 6.00, 800, 9650, 4.70, 120,
 JSON_ARRAY('基础款', '纯棉'), 'on_sale', 210),

(21012, @fashion_category_id, '城市风衣外套', '防风轻薄，春秋过渡首选',
 '防泼水面料，可收纳帽檐，通勤休闲两用。',
 'https://picsum.photos/seed/om-coat/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-coat/800/800'),
 NULL, 259.00, 399.00, 8.00, 140, 1560, 4.60, 34,
 JSON_ARRAY('春秋', '防风'), 'on_sale', 220),

(21013, @fashion_category_id, '软糯针织开衫', '温柔气质，叠穿单穿都行',
 '微弹针织面料，落肩版型，办公室空调房友好。',
 'https://picsum.photos/seed/om-cardigan/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-cardigan/800/800', 'https://picsum.photos/seed/om-cardigan-2/800/800'),
 NULL, 149.00, 229.00, 6.00, 220, 2340, 4.75, 48,
 JSON_ARRAY('温柔', '叠穿'), 'on_sale', 230),

(21014, @fashion_category_id, '直筒牛仔裤', '高腰显瘦，四季百搭',
 '弹力牛仔布，不挑身材，水洗做旧工艺。',
 'https://picsum.photos/seed/om-jeans/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-jeans/800/800'),
 NULL, 179.00, 269.00, 8.00, 310, 4870, 4.50, 76,
 JSON_ARRAY('显瘦', '百搭'), 'on_sale', 240),

(21015, @fashion_category_id, '云感拖鞋', '踩屎感鞋底，居家出门两用',
 'EVA 轻质鞋底，防滑纹理，多色可选。',
 'https://picsum.photos/seed/om-slipper/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-slipper/800/800'),
 NULL, 49.00, 79.00, 5.00, 600, 11200, 4.85, 203,
 JSON_ARRAY('居家', '轻便'), 'on_sale', 250),

(21016, @fashion_category_id, '复古棒球帽', '弯檐设计，遮阳又有型',
 '可调节帽围，刺绣 logo，四季可戴。',
 'https://picsum.photos/seed/om-cap/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-cap/800/800'),
 NULL, 69.00, 109.00, 5.00, 280, 3210, 4.40, 29,
 JSON_ARRAY('遮阳', '街拍'), 'on_sale', 260),

-- ========== 美妆 beauty ==========
(21021, @beauty_category_id, '维 C 亮肤精华', '温和提亮，改善暗沉',
 '15% 稳定维 C 衍生物，适合早 C 晚 A 护肤流程。',
 'https://picsum.photos/seed/om-serum/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-serum/800/800', 'https://picsum.photos/seed/om-serum-2/800/800'),
 NULL, 128.00, 198.00, 0.00, 190, 2890, 4.65, 61,
 JSON_ARRAY('提亮', '精华'), 'on_sale', 310),

(21022, @beauty_category_id, '氨基酸洁面乳', '温和不紧绷，敏感肌可用',
 '弱酸性配方，泡沫绵密，卸淡妆也轻松。',
 'https://picsum.photos/seed/om-cleanser/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-cleanser/800/800'),
 NULL, 59.00, 89.00, 0.00, 450, 6780, 4.80, 112,
 JSON_ARRAY('温和', '敏感肌'), 'on_sale', 320),

(21023, @beauty_category_id, '水光防晒喷雾', '清爽不搓泥，补涂方便',
 'SPF50+ PA++++，透明质酸保湿，出门补涂神器。',
 'https://picsum.photos/seed/om-sunscreen/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-sunscreen/800/800'),
 NULL, 79.00, 119.00, 0.00, 360, 4530, 4.55, 74,
 JSON_ARRAY('防晒', '喷雾'), 'on_sale', 330),

(21024, @beauty_category_id, '丝绒哑光口红', '显白不拔干，日常通勤色',
 '奶油质地，一抹成型，不易沾杯。',
 'https://picsum.photos/seed/om-lipstick/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-lipstick/800/800', 'https://picsum.photos/seed/om-lipstick-2/800/800'),
 NULL, 99.00, 149.00, 0.00, 280, 5120, 4.70, 88,
 JSON_ARRAY('显白', '哑光'), 'on_sale', 340),

(21025, @beauty_category_id, '修复面膜 5 片装', '熬夜急救，一盒救急',
 '积雪草舒缓配方，敷完皮肤软软的。',
 'https://picsum.photos/seed/om-mask/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-mask/800/800'),
 NULL, 49.90, 79.90, 0.00, 520, 8340, 4.75, 145,
 JSON_ARRAY('急救', '舒缓'), 'on_sale', 350),

(21026, @beauty_category_id, '香水小样套装', '试香友好，礼物首选',
 '含 6 支 2ml 小样，花香/木质/清新全覆盖。',
 'https://picsum.photos/seed/om-perfume/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-perfume/800/800'),
 NULL, 69.00, 99.00, 0.00, 150, 1670, 4.45, 33,
 JSON_ARRAY('试香', '礼物'), 'on_sale', 360),

-- ========== 食品 food ==========
(21031, @food_category_id, '高山乌龙茶礼盒', '清香回甘，送礼体面',
 '高山茶区春茶，独立小袋包装，约 20 泡。',
 'https://picsum.photos/seed/om-tea/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-tea/800/800', 'https://picsum.photos/seed/om-tea-2/800/800'),
 NULL, 128.00, 188.00, 8.00, 160, 980, 4.80, 27,
 JSON_ARRAY('礼盒', '清香'), 'on_sale', 410),

(21032, @food_category_id, '坚果混合装 500g', '每日一小把，办公解馋',
 '杏仁腰果核桃榛子混合，低盐烘烤。',
 'https://picsum.photos/seed/om-nuts/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-nuts/800/800'),
 NULL, 49.90, 69.90, 6.00, 480, 6230, 4.65, 91,
 JSON_ARRAY('健康', '零食'), 'on_sale', 420),

(21033, @food_category_id, '手工牛肉干', '香辣原味双拼，越嚼越香',
 '精选牛腱肉，无多余防腐剂，独立小包装。',
 'https://picsum.photos/seed/om-jerky/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-jerky/800/800'),
 NULL, 59.00, 89.00, 6.00, 340, 3890, 4.70, 58,
 JSON_ARRAY('解馋', '肉食'), 'on_sale', 430),

(21034, @food_category_id, '冷萃咖啡液 12 杯', '冰水一冲，夏日续命',
 '浓缩冷萃，无糖配方，可搭配牛奶拿铁。',
 'https://picsum.photos/seed/om-coffee/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-coffee/800/800'),
 NULL, 79.00, 119.00, 0.00, 220, 2740, 4.55, 46,
 JSON_ARRAY('咖啡', '即冲'), 'on_sale', 440),

(21035, @food_category_id, '蜂蜜柚子茶 1kg', '酸甜开胃，热饮冷饮都行',
 '真柚子果肉，冲调方便，家庭装更划算。',
 'https://picsum.photos/seed/om-yuzu/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-yuzu/800/800'),
 NULL, 45.00, 65.00, 8.00, 390, 5120, 4.60, 67,
 JSON_ARRAY('果茶', '家庭装'), 'on_sale', 450),

(21036, @food_category_id, '即食鸡胸肉 10 袋', '健身代餐，低脂高蛋白',
 '奥尔良/黑椒/原味随机混装，开袋即食。',
 'https://picsum.photos/seed/om-chicken/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-chicken/800/800'),
 NULL, 69.90, 99.90, 8.00, 280, 7450, 4.40, 103,
 JSON_ARRAY('健身', '代餐'), 'on_sale', 460),

-- ========== 家居 home ==========
(21041, @home_category_id, '记忆棉枕头', '贴合颈椎，一觉到天亮',
 '慢回弹记忆棉，可水洗外套，高度可调节。',
 'https://picsum.photos/seed/om-pillow/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-pillow/800/800', 'https://picsum.photos/seed/om-pillow-2/800/800'),
 NULL, 129.00, 199.00, 10.00, 170, 2340, 4.70, 52,
 JSON_ARRAY('睡眠', '护颈'), 'on_sale', 510),

(21042, @home_category_id, '香氛蜡烛套装', '三款香型，氛围感拉满',
 '大豆蜡手工浇注，燃烧约 25 小时/支。',
 'https://picsum.photos/seed/om-candle/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-candle/800/800'),
 NULL, 89.00, 139.00, 8.00, 200, 1560, 4.80, 41,
 JSON_ARRAY('氛围', '礼物'), 'on_sale', 520),

(21043, @home_category_id, '北欧风收纳盒', '桌面整洁神器，多格分区',
 'ABS 材质，可叠放，适合文具化妆品收纳。',
 'https://picsum.photos/seed/om-box/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-box/800/800'),
 NULL, 39.00, 59.00, 6.00, 450, 4210, 4.50, 63,
 JSON_ARRAY('收纳', '桌面'), 'on_sale', 530),

(21044, @home_category_id, '恒温电热水壶', '精准控温，泡茶冲奶都合适',
 '五段水温，防干烧保护，内壁食品级不锈钢。',
 'https://picsum.photos/seed/om-kettle/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-kettle/800/800', 'https://picsum.photos/seed/om-kettle-2/800/800'),
 NULL, 169.00, 249.00, 0.00, 130, 1890, 4.65, 38,
 JSON_ARRAY('恒温', '厨房'), 'on_sale', 540),

(21045, @home_category_id, '加厚法兰绒毯', '午睡盖腿，沙发躺平必备',
 '双面法兰绒，可机洗，多色可选。',
 'https://picsum.photos/seed/om-blanket/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-blanket/800/800'),
 NULL, 79.00, 129.00, 8.00, 260, 3670, 4.75, 71,
 JSON_ARRAY('保暖', '午睡'), 'on_sale', 550),

(21046, @home_category_id, '绿植小盆栽套装', '净化空气，新手也能养活',
 '含多肉/绿萝各一，附简易养护说明。',
 'https://picsum.photos/seed/om-plant/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-plant/800/800'),
 NULL, 49.00, 79.00, 12.00, 90, 1120, 4.35, 24,
 JSON_ARRAY('绿植', '新手'), 'on_sale', 560),

-- ========== 运动 sport ==========
(21051, @sport_category_id, '瑜伽垫 10mm', '加厚防滑，居家健身友好',
 'TPE 材质无异味，附收纳绑带，双色可选。',
 'https://picsum.photos/seed/om-yoga/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-yoga/800/800', 'https://picsum.photos/seed/om-yoga-2/800/800'),
 NULL, 89.00, 139.00, 10.00, 240, 4560, 4.70, 82,
 JSON_ARRAY('瑜伽', '居家'), 'on_sale', 610),

(21052, @sport_category_id, '可调节哑铃套装', '2.5-20kg 自由调节，省空间',
 '快拆卡扣设计，一对即可覆盖多种训练。',
 'https://picsum.photos/seed/om-dumbbell/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-dumbbell/800/800'),
 NULL, 299.00, 459.00, 15.00, 80, 980, 4.60, 29,
 JSON_ARRAY('力量', '家用'), 'on_sale', 620),

(21053, @sport_category_id, '速干运动背心', '排汗透气，夏训不闷',
 '网眼拼接设计，弹力面料，修身不紧绷。',
 'https://picsum.photos/seed/om-vest/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-vest/800/800'),
 NULL, 69.00, 109.00, 6.00, 380, 3210, 4.55, 47,
 JSON_ARRAY('速干', '夏训'), 'on_sale', 630),

(21054, @sport_category_id, '跳绳专业计数款', '电子计数，间歇训练好帮手',
 '轴承顺滑，钢丝绳可调节长度，附收纳袋。',
 'https://picsum.photos/seed/om-rope/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-rope/800/800'),
 NULL, 39.00, 69.00, 5.00, 520, 8920, 4.80, 156,
 JSON_ARRAY('有氧', '计数'), 'on_sale', 640),

(21055, @sport_category_id, '运动水杯 1L', '大容量 Tritan，无异味',
 '一键开合，刻度线清晰，健身房友好。',
 'https://picsum.photos/seed/om-bottle/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-bottle/800/800'),
 NULL, 45.00, 69.00, 0.00, 410, 5670, 4.65, 94,
 JSON_ARRAY('大容量', 'Tritan'), 'on_sale', 650),

(21056, @sport_category_id, '压缩护膝一对', '支撑减震，跑步骑行适用',
 '透气针织，硅胶防滑条，左右通用。',
 'https://picsum.photos/seed/om-knee/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-knee/800/800'),
 NULL, 59.00, 99.00, 5.00, 300, 2450, 4.50, 51,
 JSON_ARRAY('护具', '跑步'), 'on_sale', 660),

-- ========== 边界状态：草稿 / 下架（列表默认不可见，方便测过滤） ==========
(21091, @digital_category_id, '未上架智能手表草稿', '内部测试商品，请勿下单',
 '草稿状态商品，用于验证后台/过滤逻辑。',
 'https://picsum.photos/seed/om-draft/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-draft/800/800'),
 NULL, 999.00, 1299.00, 0.00, 10, 0, 0.00, 0,
 JSON_ARRAY('测试'), 'draft', 900),

(21092, @fashion_category_id, '已下架限量卫衣', '库存清空，已停止销售',
 '下架状态商品，用于验证不可购买逻辑。',
 'https://picsum.photos/seed/om-offsale/800/800',
 JSON_ARRAY('https://picsum.photos/seed/om-offsale/800/800'),
 NULL, 199.00, 299.00, 8.00, 0, 520, 4.20, 18,
 JSON_ARRAY('下架'), 'off_sale', 910);

COMMIT;
