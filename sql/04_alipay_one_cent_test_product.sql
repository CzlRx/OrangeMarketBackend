-- 已有库：增加支付宝一分钱测试商品（包邮，实付 0.01 元）
-- 用法：mysql -u root -p orange_market_simple < sql/04_alipay_one_cent_test_product.sql
-- 说明：INSERT IGNORE，可重复执行；商品 ID 固定为 21999，不与演示数据冲突

USE orange_market_simple;

SET @digital_category_id = (
    SELECT `id` FROM `product_category`
    WHERE `code` = 'digital' AND `deleted_at` = 0
    LIMIT 1
);

INSERT IGNORE INTO `product`
    (`id`, `category_id`, `name`, `subtitle`, `description`, `cover_image`, `images_json`, `video_url`,
     `sale_price`, `original_price`, `shipping_fee`, `stock`, `sales_count`, `rating_avg`, `review_count`,
     `tags_json`, `status`, `sort_order`)
VALUES
    (21999, @digital_category_id, '支付宝一分钱测试商品', '仅用于当面付联调，实付 0.01 元',
     '包邮测试商品：售价 0.01 元、运费 0 元，下单后订单总额为 0.01 元，便于真实支付宝当面付扫码联调。库存充足，可反复下单。请勿作为正式商品售卖。',
     'https://picsum.photos/seed/om-alipay-fen/800/800',
     JSON_ARRAY('https://picsum.photos/seed/om-alipay-fen/800/800'),
     NULL, 0.01, 0.01, 0.00, 9999, 0, 0.00, 0,
     JSON_ARRAY('测试', '支付宝'), 'on_sale', 1);
