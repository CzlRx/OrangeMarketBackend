-- 已有库：增加支付宝支付流水表
USE orange_market_simple;

CREATE TABLE IF NOT EXISTS `payment_transaction` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `order_id` BIGINT UNSIGNED NOT NULL,
    `out_trade_no` VARCHAR(64) NOT NULL,
    `trade_no` VARCHAR(64) NULL,
    `channel` VARCHAR(20) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending',
    `amount` DECIMAL(10,2) NOT NULL,
    `qr_code` VARCHAR(1024) NULL,
    `buyer_logon_id` VARCHAR(128) NULL,
    `paid_at` DATETIME(3) NULL,
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_out_trade_no` (`out_trade_no`),
    KEY `idx_payment_order_status` (`order_id`, `status`),
    CONSTRAINT `fk_payment_transaction_order`
        FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
