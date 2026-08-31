-- 橙子商城 MySQL 8.0 数据库结构
--
-- 说明：
-- 1. 本脚本不会删除已有表或数据，可以重复执行。
-- 2. 当前没有 SKU，因此一个商品对应一条 product_inventory 记录。
-- 3. 商品和库存由数据库工具或后续内部工具维护，订单金额以快照为准。
-- 4. MyBatis-Plus 当前配置使用 deleted_at 作为 0/1 逻辑删除标记。

CREATE DATABASE IF NOT EXISTS `orange_market`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `orange_market`;

SET NAMES utf8mb4;

-- ================================================================
-- 1. 用户与认证
-- ================================================================

CREATE TABLE IF NOT EXISTS `user_account` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `nickname` VARCHAR(64) NOT NULL COMMENT '昵称',
    `avatar_url` VARCHAR(512) NULL COMMENT '头像地址',
    `gender` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '性别：0保密，1男，2女',
    `birthday` DATE NULL COMMENT '生日',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态：active/disabled',
    `last_login_at` DATETIME(3) NULL COMMENT '最近登录时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_phone` (`phone`),
    KEY `idx_user_account_status` (`status`),
    KEY `idx_user_account_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户账户';

CREATE TABLE IF NOT EXISTS `user_login_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    `user_id` BIGINT UNSIGNED NULL COMMENT '用户 ID',
    `phone` VARCHAR(20) NOT NULL COMMENT '登录手机号',
    `login_type` VARCHAR(20) NOT NULL DEFAULT 'sms' COMMENT '登录方式',
    `login_ip` VARCHAR(64) NULL COMMENT '登录 IP',
    `user_agent` VARCHAR(512) NULL COMMENT '浏览器 User-Agent',
    `login_result` VARCHAR(20) NOT NULL COMMENT 'success/failed',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_login_log_user_time` (`user_id`, `created_at`),
    KEY `idx_user_login_log_phone_time` (`phone`, `created_at`),
    CONSTRAINT `fk_user_login_log_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录日志';

-- 短信验证码只存 Redis；此表只保存发送审计摘要，验证码正文禁止落库。
CREATE TABLE IF NOT EXISTS `auth_sms_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '短信日志主键',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `purpose` VARCHAR(20) NOT NULL DEFAULT 'login' COMMENT '用途：login/register',
    `code_digest` VARCHAR(128) NULL COMMENT '验证码摘要',
    `request_ip` VARCHAR(64) NULL COMMENT '请求 IP',
    `status` VARCHAR(20) NOT NULL DEFAULT 'sent' COMMENT 'sent/used/expired/failed',
    `expires_at` DATETIME(3) NOT NULL COMMENT '过期时间',
    `used_at` DATETIME(3) NULL COMMENT '使用时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_auth_sms_log_phone_time` (`phone`, `created_at`),
    KEY `idx_auth_sms_log_expire` (`status`, `expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短信验证码发送日志';

-- ================================================================
-- 2. 商品、分类、媒体与库存
-- ================================================================

CREATE TABLE IF NOT EXISTS `product_category` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '分类主键',
    `parent_id` BIGINT UNSIGNED NULL COMMENT '父分类 ID',
    `code` VARCHAR(64) NOT NULL COMMENT '分类编码',
    `name` VARCHAR(64) NOT NULL COMMENT '分类名称',
    `eyebrow` VARCHAR(128) NULL COMMENT '英文副标题',
    `color` VARCHAR(16) NULL COMMENT '前端分类背景色',
    `icon_key` VARCHAR(64) NULL COMMENT '前端图标名称',
    `is_virtual` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否虚拟分类，例如秒杀专区',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_category_code` (`code`),
    KEY `idx_product_category_parent` (`parent_id`, `status`, `sort_order`),
    KEY `idx_product_category_deleted` (`deleted_at`),
    CONSTRAINT `fk_product_category_parent`
        FOREIGN KEY (`parent_id`) REFERENCES `product_category` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类';

CREATE TABLE IF NOT EXISTS `product` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品主键',
    `category_id` BIGINT UNSIGNED NULL COMMENT '普通商品分类 ID',
    `name` VARCHAR(255) NOT NULL COMMENT '商品名称',
    `subtitle` VARCHAR(255) NULL COMMENT '商品副标题',
    `description` TEXT NULL COMMENT '商品描述',
    `sale_price` DECIMAL(10,2) NOT NULL COMMENT '普通售价',
    `original_price` DECIMAL(10,2) NOT NULL COMMENT '划线原价',
    `shipping_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该商品固定运费',
    `sales_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '销量统计',
    `rating_avg` DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '综合评分',
    `review_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评价数量',
    `tags_json` JSON NULL COMMENT '展示标签数组',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/on_sale/off_sale',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_product_category_status_sort` (`category_id`, `status`, `sort_order`, `id`),
    KEY `idx_product_status_sales` (`status`, `sales_count`),
    KEY `idx_product_deleted` (`deleted_at`),
    FULLTEXT KEY `ft_product_search` (`name`, `subtitle`, `description`),
    CONSTRAINT `fk_product_category`
        FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品主表';

CREATE TABLE IF NOT EXISTS `product_media` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '媒体主键',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `media_type` VARCHAR(20) NOT NULL COMMENT 'image/video',
    `media_url` VARCHAR(512) NOT NULL COMMENT '媒体访问地址',
    `object_key` VARCHAR(512) NULL COMMENT '对象存储 Key',
    `is_cover` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否封面',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '媒体顺序',
    `duration_seconds` INT UNSIGNED NULL COMMENT '视频时长，图片为空',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_product_media_product_sort` (`product_id`, `sort_order`),
    CONSTRAINT `fk_product_media_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品图片和视频';

CREATE TABLE IF NOT EXISTS `product_inventory` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存主键',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `stock_total` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总库存',
    `available_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '可售库存',
    `locked_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '锁定库存',
    `sold_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已售库存',
    `version` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_inventory_product` (`product_id`),
    CONSTRAINT `fk_product_inventory_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通商品库存';

-- ================================================================
-- 3. 秒杀
-- ================================================================

CREATE TABLE IF NOT EXISTS `seckill_activity` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '秒杀活动主键',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `activity_name` VARCHAR(128) NOT NULL COMMENT '活动名称',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价',
    `stock_total` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '秒杀总库存',
    `available_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '秒杀可售库存',
    `locked_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '秒杀锁定库存',
    `sold_stock` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '秒杀已售库存',
    `purchase_limit` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人限购数量',
    `start_time` DATETIME(3) NOT NULL COMMENT '开始时间',
    `end_time` DATETIME(3) NOT NULL COMMENT '结束时间',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT 'draft/not_started/running/ended/sold_out',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_seckill_activity_status_time` (`status`, `start_time`, `end_time`),
    KEY `idx_seckill_activity_product_time` (`product_id`, `start_time`, `end_time`),
    CONSTRAINT `fk_seckill_activity_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='秒杀活动';

-- 每次用户尝试对应一条记录，取消订单后可以重新抢购；最终限购判断由事务和 Redis 原子操作共同保证。
CREATE TABLE IF NOT EXISTS `seckill_user_purchase` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '秒杀购买记录主键',
    `activity_id` BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `quantity` INT UNSIGNED NOT NULL COMMENT '购买数量',
    `status` VARCHAR(20) NOT NULL DEFAULT 'locked' COMMENT 'locked/success/cancelled',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_seckill_purchase_attempt` (`activity_id`, `user_id`, `order_id`),
    KEY `idx_seckill_purchase_user` (`activity_id`, `user_id`, `status`),
    KEY `idx_seckill_purchase_order` (`order_id`),
    CONSTRAINT `fk_seckill_purchase_activity`
        FOREIGN KEY (`activity_id`) REFERENCES `seckill_activity` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_seckill_purchase_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户秒杀购买记录';

-- ================================================================
-- 4. 购物车与收货地址
-- ================================================================

CREATE TABLE IF NOT EXISTS `cart_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '购物车明细主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `seckill_activity_id` BIGINT UNSIGNED NULL COMMENT '关联秒杀活动，可为空',
    `quantity` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '商品数量',
    `selected` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否选中',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cart_item_user_product` (`user_id`, `product_id`),
    KEY `idx_cart_item_user_selected` (`user_id`, `selected`),
    CONSTRAINT `fk_cart_item_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_cart_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_cart_item_seckill_activity`
        FOREIGN KEY (`seckill_activity_id`) REFERENCES `seckill_activity` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车商品';

CREATE TABLE IF NOT EXISTS `user_address` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `receiver` VARCHAR(64) NOT NULL COMMENT '收货人',
    `phone` VARCHAR(20) NOT NULL COMMENT '收货手机号',
    `province_code` VARCHAR(32) NULL COMMENT '省编码',
    `province` VARCHAR(64) NOT NULL COMMENT '省名称',
    `city_code` VARCHAR(32) NULL COMMENT '市编码',
    `city` VARCHAR(64) NOT NULL COMMENT '市名称',
    `district_code` VARCHAR(32) NULL COMMENT '区县编码',
    `district` VARCHAR(64) NOT NULL COMMENT '区县名称',
    `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
    `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否默认地址',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除，1已删除',
    PRIMARY KEY (`id`),
    KEY `idx_user_address_user_default` (`user_id`, `is_default`, `deleted_at`),
    CONSTRAINT `fk_user_address_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收货地址';

-- ================================================================
-- 5. 订单、库存锁定、支付与物流
-- ================================================================

CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键',
    `order_no` VARCHAR(32) NOT NULL COMMENT '对外订单号',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending_payment' COMMENT '订单状态',
    `subtotal_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '商品金额',
    `shipping_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '总运费',
    `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '应付总额',
    `buyer_remark` VARCHAR(100) NULL COMMENT '买家备注',
    `payment_expire_at` DATETIME(3) NULL COMMENT '支付截止时间',
    `paid_at` DATETIME(3) NULL COMMENT '支付时间',
    `shipped_at` DATETIME(3) NULL COMMENT '发货时间',
    `received_at` DATETIME(3) NULL COMMENT '确认收货时间',
    `completed_at` DATETIME(3) NULL COMMENT '订单完成时间',
    `cancelled_at` DATETIME(3) NULL COMMENT '取消时间',
    `cancel_reason` VARCHAR(255) NULL COMMENT '取消原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '下单时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_orders_order_no` (`order_no`),
    KEY `idx_orders_user_status_time` (`user_id`, `status`, `created_at`),
    KEY `idx_orders_expire` (`status`, `payment_expire_at`),
    CONSTRAINT `fk_orders_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS `order_address_snapshot` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '地址快照主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `receiver` VARCHAR(64) NOT NULL COMMENT '收货人',
    `phone` VARCHAR(20) NOT NULL COMMENT '收货手机号',
    `province` VARCHAR(64) NOT NULL COMMENT '省名称',
    `city` VARCHAR(64) NOT NULL COMMENT '市名称',
    `district` VARCHAR(64) NOT NULL COMMENT '区县名称',
    `detail` VARCHAR(255) NOT NULL COMMENT '详细地址',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_address_snapshot_order` (`order_id`),
    CONSTRAINT `fk_order_address_snapshot_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单收货地址快照';

CREATE TABLE IF NOT EXISTS `order_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单商品主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `product_id` BIGINT UNSIGNED NULL COMMENT '商品 ID，商品删除后可为空',
    `seckill_activity_id` BIGINT UNSIGNED NULL COMMENT '秒杀活动 ID，可为空',
    `product_name` VARCHAR(255) NOT NULL COMMENT '下单时商品名称快照',
    `product_image` VARCHAR(512) NULL COMMENT '下单时商品图片快照',
    `unit_price` DECIMAL(10,2) NOT NULL COMMENT '成交单价',
    `quantity` INT UNSIGNED NOT NULL COMMENT '购买数量',
    `line_amount` DECIMAL(10,2) NOT NULL COMMENT '商品行金额',
    `shipping_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该商品订单运费',
    `is_seckill` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否秒杀商品',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_item_order` (`order_id`),
    KEY `idx_order_item_product_time` (`product_id`, `created_at`),
    CONSTRAINT `fk_order_item_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_order_item_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_order_item_seckill_activity`
        FOREIGN KEY (`seckill_activity_id`) REFERENCES `seckill_activity` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单商品明细和商品快照';

CREATE TABLE IF NOT EXISTS `inventory_reservation` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '库存锁定主键',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `activity_id` BIGINT UNSIGNED NULL COMMENT '秒杀活动 ID，可为空',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `order_item_id` BIGINT UNSIGNED NOT NULL COMMENT '订单商品 ID',
    `quantity` INT UNSIGNED NOT NULL COMMENT '锁定数量',
    `reservation_type` VARCHAR(20) NOT NULL DEFAULT 'normal' COMMENT 'normal/seckill',
    `status` VARCHAR(20) NOT NULL DEFAULT 'locked' COMMENT 'locked/released/deducted',
    `expire_at` DATETIME(3) NOT NULL COMMENT '锁定过期时间',
    `released_at` DATETIME(3) NULL COMMENT '释放时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_inventory_reservation_item_type` (`order_item_id`, `reservation_type`),
    KEY `idx_inventory_reservation_expire` (`status`, `expire_at`),
    KEY `idx_inventory_reservation_order` (`order_id`),
    CONSTRAINT `fk_inventory_reservation_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_inventory_reservation_activity`
        FOREIGN KEY (`activity_id`) REFERENCES `seckill_activity` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_inventory_reservation_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_inventory_reservation_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='库存锁定记录';

CREATE TABLE IF NOT EXISTS `order_status_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '状态记录主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `from_status` VARCHAR(32) NULL COMMENT '原状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '新状态',
    `operator_type` VARCHAR(20) NOT NULL DEFAULT 'system' COMMENT 'user/system',
    `operator_id` BIGINT UNSIGNED NULL COMMENT '操作用户 ID',
    `reason` VARCHAR(255) NULL COMMENT '状态变更原因',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_status_history_order_time` (`order_id`, `created_at`),
    CONSTRAINT `fk_order_status_history_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_order_status_history_operator`
        FOREIGN KEY (`operator_id`) REFERENCES `user_account` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单状态流转记录';

CREATE TABLE IF NOT EXISTS `payment_transaction` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '支付流水主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `payment_no` VARCHAR(64) NOT NULL COMMENT '支付流水号',
    `payment_method` VARCHAR(20) NOT NULL COMMENT 'wechat/alipay',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed/cancelled/refunded',
    `idempotency_key` VARCHAR(128) NOT NULL COMMENT '支付幂等键',
    `provider_transaction_no` VARCHAR(128) NULL COMMENT '第三方支付流水号',
    `paid_at` DATETIME(3) NULL COMMENT '支付完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_transaction_no` (`payment_no`),
    UNIQUE KEY `uk_payment_transaction_idempotency` (`idempotency_key`),
    KEY `idx_payment_transaction_order_time` (`order_id`, `created_at`),
    CONSTRAINT `fk_payment_transaction_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_payment_transaction_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支付交易流水';

CREATE TABLE IF NOT EXISTS `order_shipment` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '物流主键',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `logistics_company` VARCHAR(64) NULL COMMENT '物流公司',
    `tracking_no` VARCHAR(128) NULL COMMENT '运单号',
    `status` VARCHAR(20) NOT NULL DEFAULT 'shipped' COMMENT 'shipped/delivered',
    `shipped_at` DATETIME(3) NULL COMMENT '发货时间',
    `delivered_at` DATETIME(3) NULL COMMENT '签收时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_shipment_tracking_no` (`tracking_no`),
    KEY `idx_order_shipment_order` (`order_id`),
    CONSTRAINT `fk_order_shipment_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单物流信息';

-- ================================================================
-- 6. 售后与退款
-- ================================================================

CREATE TABLE IF NOT EXISTS `after_sale_request` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '售后申请主键',
    `after_sale_no` VARCHAR(64) NOT NULL COMMENT '售后单号',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `type` VARCHAR(20) NOT NULL DEFAULT 'refund_only' COMMENT 'refund_only/return_refund',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected/refunded/cancelled',
    `reason` VARCHAR(128) NULL COMMENT '售后原因',
    `description` VARCHAR(1000) NULL COMMENT '问题描述',
    `requested_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '申请金额',
    `approved_amount` DECIMAL(10,2) NULL COMMENT '审核金额',
    `applied_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '申请时间',
    `processed_at` DATETIME(3) NULL COMMENT '处理时间',
    `closed_at` DATETIME(3) NULL COMMENT '关闭时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_after_sale_request_no` (`after_sale_no`),
    KEY `idx_after_sale_request_order` (`order_id`, `status`),
    KEY `idx_after_sale_request_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_after_sale_request_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_after_sale_request_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售后申请';

CREATE TABLE IF NOT EXISTS `after_sale_item` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '售后商品主键',
    `after_sale_id` BIGINT UNSIGNED NOT NULL COMMENT '售后申请 ID',
    `order_item_id` BIGINT UNSIGNED NOT NULL COMMENT '订单商品 ID',
    `quantity` INT UNSIGNED NOT NULL COMMENT '售后数量',
    `amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '售后金额',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_after_sale_item` (`after_sale_id`, `order_item_id`),
    CONSTRAINT `fk_after_sale_item_request`
        FOREIGN KEY (`after_sale_id`) REFERENCES `after_sale_request` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_after_sale_item_order_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='售后商品明细';

CREATE TABLE IF NOT EXISTS `refund_transaction` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '退款流水主键',
    `after_sale_id` BIGINT UNSIGNED NOT NULL COMMENT '售后申请 ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `refund_no` VARCHAR(64) NOT NULL COMMENT '退款流水号',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/success/failed',
    `payment_method` VARCHAR(20) NULL COMMENT '原支付方式',
    `provider_refund_no` VARCHAR(128) NULL COMMENT '第三方退款号',
    `refunded_at` DATETIME(3) NULL COMMENT '退款完成时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_transaction_no` (`refund_no`),
    KEY `idx_refund_transaction_after_sale` (`after_sale_id`),
    KEY `idx_refund_transaction_order` (`order_id`),
    CONSTRAINT `fk_refund_transaction_after_sale`
        FOREIGN KEY (`after_sale_id`) REFERENCES `after_sale_request` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_refund_transaction_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='退款交易流水';

-- ================================================================
-- 7. 收藏、浏览足迹与评价
-- ================================================================

CREATE TABLE IF NOT EXISTS `user_favorite` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '收藏主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_favorite_user_product` (`user_id`, `product_id`),
    KEY `idx_user_favorite_user_time` (`user_id`, `created_at`),
    CONSTRAINT `fk_user_favorite_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_user_favorite_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏';

CREATE TABLE IF NOT EXISTS `user_browse_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '浏览记录主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `viewed_at` DATETIME(3) NOT NULL COMMENT '最近浏览时间',
    `price_at_view` DECIMAL(10,2) NOT NULL COMMENT '浏览时有效售价',
    `last_notified_price` DECIMAL(10,2) NULL COMMENT '最近提醒价格',
    `price_drop_notified_at` DATETIME(3) NULL COMMENT '最近降价提醒时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_browse_history_user_product` (`user_id`, `product_id`),
    KEY `idx_user_browse_history_user_time` (`user_id`, `viewed_at`),
    CONSTRAINT `fk_user_browse_history_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_user_browse_history_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户浏览足迹';

CREATE TABLE IF NOT EXISTS `product_review` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评价主键',
    `product_id` BIGINT UNSIGNED NOT NULL COMMENT '商品 ID',
    `order_id` BIGINT UNSIGNED NOT NULL COMMENT '订单 ID',
    `order_item_id` BIGINT UNSIGNED NOT NULL COMMENT '订单商品 ID',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `content` TEXT NOT NULL COMMENT '评价内容',
    `quality_score` TINYINT UNSIGNED NOT NULL COMMENT '质量评分，1-5',
    `service_score` TINYINT UNSIGNED NOT NULL COMMENT '服务评分，1-5',
    `logistics_score` TINYINT UNSIGNED NOT NULL COMMENT '物流评分，1-5',
    `anonymous` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否匿名',
    `status` VARCHAR(20) NOT NULL DEFAULT 'visible' COMMENT 'visible/hidden/pending',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_product_review_user_item` (`user_id`, `order_item_id`),
    KEY `idx_product_review_product_status_time` (`product_id`, `status`, `created_at`),
    KEY `idx_product_review_product_quality` (`product_id`, `quality_score`),
    CONSTRAINT `fk_product_review_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_product_review_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_product_review_order_item`
        FOREIGN KEY (`order_item_id`) REFERENCES `order_item` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT `fk_product_review_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价';

CREATE TABLE IF NOT EXISTS `review_media` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评价媒体主键',
    `review_id` BIGINT UNSIGNED NOT NULL COMMENT '评价 ID',
    `media_type` VARCHAR(20) NOT NULL COMMENT 'image/video',
    `media_url` VARCHAR(512) NOT NULL COMMENT '媒体访问地址',
    `object_key` VARCHAR(512) NULL COMMENT '对象存储 Key',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '媒体顺序',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_review_media_review_sort` (`review_id`, `sort_order`),
    CONSTRAINT `fk_review_media_review`
        FOREIGN KEY (`review_id`) REFERENCES `product_review` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价图片和视频';

-- ================================================================
-- 8. 客服与搜索历史
-- ================================================================

CREATE TABLE IF NOT EXISTS `service_session` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '客服会话主键',
    `user_id` BIGINT UNSIGNED NULL COMMENT '登录用户 ID，游客为空',
    `visitor_token` VARCHAR(128) NULL COMMENT '游客会话标识',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/closed',
    `last_message_at` DATETIME(3) NULL COMMENT '最后消息时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `closed_at` DATETIME(3) NULL COMMENT '关闭时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_session_user_status` (`user_id`, `status`, `last_message_at`),
    KEY `idx_service_session_visitor` (`visitor_token`, `status`),
    CONSTRAINT `fk_service_session_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服会话';

CREATE TABLE IF NOT EXISTS `service_message` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '客服消息主键',
    `session_id` BIGINT UNSIGNED NOT NULL COMMENT '会话 ID',
    `sender_type` VARCHAR(20) NOT NULL COMMENT 'user/service/system',
    `sender_id` BIGINT UNSIGNED NULL COMMENT '发送者用户 ID',
    `message_type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT 'text/product_card/order_card/faq',
    `content` TEXT NULL COMMENT '消息文本',
    `product_id` BIGINT UNSIGNED NULL COMMENT '商品卡片关联商品',
    `order_id` BIGINT UNSIGNED NULL COMMENT '订单卡片关联订单',
    `payload_json` JSON NULL COMMENT '卡片快照数据',
    `read_at` DATETIME(3) NULL COMMENT '已读时间',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '消息时间',
    PRIMARY KEY (`id`),
    KEY `idx_service_message_session_time` (`session_id`, `created_at`, `id`),
    KEY `idx_service_message_sender` (`sender_id`, `created_at`),
    CONSTRAINT `fk_service_message_session`
        FOREIGN KEY (`session_id`) REFERENCES `service_session` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_service_message_sender`
        FOREIGN KEY (`sender_id`) REFERENCES `user_account` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_service_message_product`
        FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT `fk_service_message_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服消息';

CREATE TABLE IF NOT EXISTS `service_faq` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'FAQ 主键',
    `question` VARCHAR(255) NOT NULL COMMENT '问题',
    `answer` TEXT NOT NULL COMMENT '自动回复内容',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_service_faq_question` (`question`),
    KEY `idx_service_faq_status_sort` (`status`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服常见问题';

CREATE TABLE IF NOT EXISTS `user_search_history` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '搜索记录主键',
    `user_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    `keyword` VARCHAR(128) NOT NULL COMMENT '搜索关键词',
    `searched_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '搜索时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_search_history_user_keyword` (`user_id`, `keyword`),
    KEY `idx_user_search_history_user_time` (`user_id`, `searched_at`),
    CONSTRAINT `fk_user_search_history_user`
        FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户搜索历史';

-- ================================================================
-- 9. 前端 Mock 对应的基础数据
-- ================================================================

INSERT IGNORE INTO `product_category`
    (`code`, `name`, `eyebrow`, `color`, `icon_key`, `is_virtual`, `sort_order`, `status`)
VALUES
    ('digital', '数码', 'SMART LIFE', '#e7f5ff', 'Laptop', 0, 10, 'active'),
    ('fashion', '服饰', 'NEW SEASON', '#fff0f3', 'Shirt', 0, 20, 'active'),
    ('beauty', '美妆', 'GLOW UP', '#fff2e9', 'Sparkles', 0, 30, 'active'),
    ('food', '食品', 'TASTY DAY', '#fff7db', 'Cookie', 0, 40, 'active'),
    ('home', '家居', 'COZY HOME', '#ecf8ec', 'LampDesk', 0, 50, 'active'),
    ('sport', '运动', 'MOVE MORE', '#eaf4ff', 'Dumbbell', 0, 60, 'active'),
    ('seckill', '秒杀专区', 'LIMITED DEALS', '#ffe9d8', 'Flame', 1, 70, 'active');

INSERT IGNORE INTO `service_faq`
    (`question`, `answer`, `sort_order`, `status`)
VALUES
    ('商品什么时候发货？', '现货订单会在 24 小时内安排发出，请耐心等待物流更新哦。', 10, 'active'),
    ('可以修改收货地址吗？', '订单提交前可以在结算页选择其他收货地址，订单提交后请尽快联系客服。', 20, 'active'),
    ('如何申请退款？', '可以在订单详情中发起退款申请，我们会尽快为你处理。', 30, 'active'),
    ('秒杀商品可以退换吗？', '秒杀商品的售后规则与商品详情页说明为准，如有问题请联系客服。', 40, 'active');

-- ================================================================
-- 10. 建模约定与后续实现提示
-- ================================================================

-- 订单状态：
-- pending_payment -> pending_shipment -> pending_receipt -> pending_review -> completed
-- pending_payment -> cancelled
-- pending_shipment/pending_receipt -> refunding -> refunded
--
-- 订单创建、支付成功、超时取消必须在事务中完成库存状态变更。
-- 30 分钟自动取消由后端定时任务或延迟队列执行，不依赖前端倒计时。
-- 商品列表的 keyword、price、sales 排序必须在分页前执行。
-- 评价列表的 media 筛选应通过 review_media 判断。
-- 当前不创建 coupon、invoice、sku、admin、merchant、wallet、point 等表。
