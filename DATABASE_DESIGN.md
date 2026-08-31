# 橙子商城数据库设计

## 1. 文档说明

本文档根据当前前端项目的页面、TypeScript 类型、Zustand 状态和 Mock 数据整理，作为橙子商城后端数据库设计和接口设计的依据。

前端项目中的主要参考文件：

- `OrangeMarketFrontend/src/types.ts`
- `OrangeMarketFrontend/src/store/useStore.ts`
- `OrangeMarketFrontend/src/data/mockData.ts`
- `OrangeMarketFrontend/src/pages/CheckoutPage.tsx`
- `OrangeMarketFrontend/src/pages/OrderDetailPage.tsx`
- `OrangeMarketFrontend/src/pages/ReviewsPage.tsx`
- `OrangeMarketFrontend/src/pages/ServicePage.tsx`

当前项目范围：

- 单体后端架构，仅提供 C 端用户功能
- 暂无后台管理系统，商品、库存和活动数据通过 SQL 或数据库工具维护
- 手机号 + 短信验证码登录，首次登录自动注册
- 暂无真实支付，先实现模拟支付
- 暂无 SKU 规格，当前一个商品对应一个库存单元
- 不使用优惠券和发票
- 支持 PC 和移动端，但数据库设计不区分终端

## 2. 基础规范

建议使用 MySQL 8.0，并遵循以下约定：

- 存储引擎使用 InnoDB
- 字符集使用 `utf8mb4`
- 主键使用 `BIGINT UNSIGNED`
- API 返回给前端的 ID 可以统一转换为字符串
- 金额使用 `DECIMAL(10,2)`，禁止使用 `FLOAT` 或 `DOUBLE`
- 时间字段使用 `DATETIME(3)`，服务端统一使用 UTC 存储
- 状态字段使用 `VARCHAR(32)`，不使用 MySQL `ENUM`
- 商品、分类、地址使用 `deleted_at TINYINT` 作为 `0/1` 软删除标记，和当前 MyBatis-Plus 配置保持一致
- 订单商品必须保存商品快照
- 所有关键业务接口需要支持幂等处理
- 所有库存扣减、订单创建、支付回调必须使用数据库事务

## 3. MySQL 表总览

首期建议创建以下业务表：

| 模块 | 表名 | 说明 |
| --- | --- | --- |
| 用户 | `user_account` | 用户基本资料和手机号 |
| 用户 | `user_login_log` | 登录日志，可选但建议保留 |
| 用户 | `auth_sms_log` | 短信发送审计日志，可选 |
| 商品 | `product_category` | 普通商品分类 |
| 商品 | `product` | 商品主表 |
| 商品 | `product_media` | 商品图片和视频 |
| 商品 | `product_inventory` | 普通商品库存 |
| 秒杀 | `seckill_activity` | 秒杀活动和秒杀价格 |
| 秒杀 | `seckill_user_purchase` | 用户秒杀限购记录 |
| 购物车 | `cart_item` | 用户购物车商品 |
| 地址 | `user_address` | 用户收货地址 |
| 订单 | `orders` | 订单主表 |
| 订单 | `order_address_snapshot` | 订单收货地址快照 |
| 订单 | `order_item` | 订单商品明细和商品快照 |
| 订单 | `inventory_reservation` | 下单后的库存锁定记录 |
| 订单 | `order_status_history` | 订单状态流转记录 |
| 支付 | `payment_transaction` | 模拟支付及未来真实支付流水 |
| 物流 | `order_shipment` | 发货和物流信息 |
| 售后 | `after_sale_request` | 售后申请 |
| 售后 | `after_sale_item` | 售后商品明细 |
| 售后 | `refund_transaction` | 退款流水 |
| 互动 | `user_favorite` | 用户收藏 |
| 互动 | `user_browse_history` | 用户浏览足迹 |
| 评价 | `product_review` | 商品评价 |
| 评价 | `review_media` | 评价图片和视频 |
| 客服 | `service_session` | 客服会话 |
| 客服 | `service_message` | 客服消息 |
| 客服 | `service_faq` | 常见问题 |
| 搜索 | `user_search_history` | 用户搜索历史 |

## 4. 用户与认证表

### 4.1 `user_account`

对应前端 `User` 类型。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `phone` | `VARCHAR(20)` | 手机号，唯一 |
| `nickname` | `VARCHAR(64)` | 昵称 |
| `avatar_url` | `VARCHAR(512)` | 头像地址 |
| `gender` | `TINYINT` | `0` 保密、`1` 男、`2` 女 |
| `birthday` | `DATE` | 生日，可为空 |
| `status` | `VARCHAR(20)` | `active`、`disabled` |
| `last_login_at` | `DATETIME(3)` | 最近登录时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |
| `deleted_at` | `TINYINT` | 逻辑删除：0未删除，1已删除 |

约束和索引：

- `UNIQUE(phone)`
- `INDEX(status)`

不需要密码字段，因为当前只使用手机号验证码登录。

### 4.2 `user_login_log`

用于登录审计和异常排查。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID，可为空 |
| `phone` | `VARCHAR(20)` | 登录手机号 |
| `login_type` | `VARCHAR(20)` | 当前为 `sms` |
| `login_ip` | `VARCHAR(64)` | 登录 IP |
| `user_agent` | `VARCHAR(512)` | 浏览器信息 |
| `login_result` | `VARCHAR(20)` | `success` 或 `failed` |
| `created_at` | `DATETIME(3)` | 登录时间 |

验证码正文、图形验证码答案和登录 Token 不强制写入 MySQL，使用 Redis 保存即可。

## 5. 商品与分类表

### 5.1 `product_category`

对应前端 `Category` 类型。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `parent_id` | `BIGINT UNSIGNED` | 父分类，可为空 |
| `code` | `VARCHAR(64)` | 分类编码，唯一 |
| `name` | `VARCHAR(64)` | 分类名称 |
| `eyebrow` | `VARCHAR(128)` | 分类英文副标题 |
| `color` | `VARCHAR(16)` | 前端分类背景色 |
| `icon_key` | `VARCHAR(64)` | 前端图标名称 |
| `is_virtual` | `TINYINT` | 是否虚拟分类，例如秒杀专区 |
| `sort_order` | `INT` | 排序值 |
| `status` | `VARCHAR(20)` | `active`、`disabled` |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |
| `deleted_at` | `TINYINT` | 逻辑删除：0未删除，1已删除 |

索引：

- `UNIQUE(code)`
- `INDEX(parent_id, status, sort_order)`

“秒杀专区”建议作为虚拟筛选，不建议把秒杀商品真实归入普通分类。秒杀商品是否属于专区，应由是否存在有效的 `seckill_activity` 决定。如果为了兼容前端展示需要，可以增加一个 `code=seckill` 的虚拟分类，但不建议将其作为商品的真实 `category_id`。

### 5.2 `product`

商品主表，保存商品基础信息、普通售价和展示统计数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `category_id` | `BIGINT UNSIGNED` | 普通商品分类 |
| `name` | `VARCHAR(255)` | 商品名称 |
| `subtitle` | `VARCHAR(255)` | 商品副标题 |
| `description` | `TEXT` | 商品描述 |
| `sale_price` | `DECIMAL(10,2)` | 普通售价 |
| `original_price` | `DECIMAL(10,2)` | 划线原价 |
| `shipping_fee` | `DECIMAL(10,2)` | 商品固定运费 |
| `sales_count` | `INT UNSIGNED` | 销量统计 |
| `rating_avg` | `DECIMAL(3,2)` | 综合评分 |
| `review_count` | `INT UNSIGNED` | 评价数量 |
| `tags_json` | `JSON` | 当前用于展示的标签数组 |
| `status` | `VARCHAR(20)` | `on_sale`、`off_sale`、`draft` |
| `sort_order` | `INT` | 商品排序值 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |
| `deleted_at` | `TINYINT` | 逻辑删除：0未删除，1已删除 |

索引：

- `INDEX(category_id, status, sort_order, id)`
- `INDEX(status, sales_count)`
- `FULLTEXT(name, subtitle, description)`

当前商品标签仅用于商品卡片展示，暂时使用 `JSON` 即可。以后如果需要按标签筛选，再拆分为 `product_tag` 和 `product_tag_rel` 两张表。

### 5.3 `product_media`

保存商品详情页的图片和视频。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `media_type` | `VARCHAR(20)` | `image` 或 `video` |
| `media_url` | `VARCHAR(512)` | 访问地址 |
| `object_key` | `VARCHAR(512)` | 对象存储 Key |
| `is_cover` | `TINYINT` | 是否封面 |
| `sort_order` | `INT` | 媒体顺序 |
| `duration_seconds` | `INT UNSIGNED` | 视频时长，可为空 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

索引：

- `INDEX(product_id, sort_order)`

图片和视频文件不直接存储到 MySQL。

### 5.4 `product_inventory`

当前没有 SKU，一个商品对应一条库存记录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `stock_total` | `INT UNSIGNED` | 总库存 |
| `available_stock` | `INT UNSIGNED` | 可售库存 |
| `locked_stock` | `INT UNSIGNED` | 已锁定库存 |
| `sold_stock` | `INT UNSIGNED` | 已售库存 |
| `version` | `INT UNSIGNED` | 乐观锁版本号 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束：

- `UNIQUE(product_id)`
- 更新库存时必须带上 `available_stock >= quantity` 条件

以后增加 SKU 时，再增加 `product_sku`，将库存粒度从商品调整为 SKU。

## 6. 秒杀相关表

### 6.1 `seckill_activity`

保存秒杀活动，不直接覆盖商品普通售价。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `activity_name` | `VARCHAR(128)` | 活动名称 |
| `seckill_price` | `DECIMAL(10,2)` | 秒杀价 |
| `stock_total` | `INT UNSIGNED` | 秒杀总库存 |
| `available_stock` | `INT UNSIGNED` | 秒杀可售库存 |
| `locked_stock` | `INT UNSIGNED` | 秒杀锁定库存 |
| `sold_stock` | `INT UNSIGNED` | 秒杀已售库存 |
| `purchase_limit` | `INT UNSIGNED` | 每人限购数量 |
| `start_time` | `DATETIME(3)` | 开始时间 |
| `end_time` | `DATETIME(3)` | 结束时间 |
| `status` | `VARCHAR(20)` | `draft`、`not_started`、`running`、`ended`、`sold_out` |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

索引：

- `INDEX(status, start_time, end_time)`
- `INDEX(product_id, start_time, end_time)`

### 6.2 `seckill_user_purchase`

保存用户秒杀购买数量，用于限购和防止重复抢购。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `activity_id` | `BIGINT UNSIGNED` | 秒杀活动 ID |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `order_id` | `BIGINT UNSIGNED` | 关联订单 |
| `quantity` | `INT UNSIGNED` | 已购买数量 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束和索引：

- `UNIQUE(activity_id, user_id, order_id)`，同一用户同一活动的不同订单尝试可分别记录
- `INDEX(activity_id, user_id, status)`，用于计算有效限购数量
- `INDEX(user_id, created_at)`

## 7. 购物车与地址表

### 7.1 `cart_item`

对应前端 `CartItem`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `seckill_activity_id` | `BIGINT UNSIGNED` | 秒杀活动，可为空 |
| `quantity` | `INT UNSIGNED` | 商品数量 |
| `selected` | `TINYINT` | 是否选中 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束：

- `UNIQUE(user_id, product_id)`
- `INDEX(user_id, selected)`

游客购物车可以继续使用浏览器本地存储，登录后再合并到该表。

### 7.2 `user_address`

对应前端 `Address`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `receiver` | `VARCHAR(64)` | 收货人 |
| `phone` | `VARCHAR(20)` | 收货手机号 |
| `province_code` | `VARCHAR(32)` | 省编码，可为空 |
| `province` | `VARCHAR(64)` | 省名称 |
| `city_code` | `VARCHAR(32)` | 市编码，可为空 |
| `city` | `VARCHAR(64)` | 市名称 |
| `district_code` | `VARCHAR(32)` | 区县编码，可为空 |
| `district` | `VARCHAR(64)` | 区县名称 |
| `detail` | `VARCHAR(255)` | 详细地址 |
| `is_default` | `TINYINT` | 是否默认地址 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |
| `deleted_at` | `TINYINT` | 逻辑删除：0未删除，1已删除 |

索引：

- `INDEX(user_id, is_default, deleted_at)`

设置默认地址时，需要在事务中先取消用户原默认地址，再设置新默认地址。

## 8. 订单、库存与支付表

### 8.1 `orders`

订单主表。由于 `order` 可能是数据库保留字，表名使用 `orders`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_no` | `VARCHAR(32)` | 对外订单号，唯一 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `status` | `VARCHAR(32)` | 订单当前状态 |
| `subtotal_amount` | `DECIMAL(10,2)` | 商品金额 |
| `shipping_amount` | `DECIMAL(10,2)` | 总运费 |
| `total_amount` | `DECIMAL(10,2)` | 应付总额 |
| `buyer_remark` | `VARCHAR(100)` | 买家备注 |
| `payment_expire_at` | `DATETIME(3)` | 支付截止时间 |
| `paid_at` | `DATETIME(3)` | 支付时间 |
| `shipped_at` | `DATETIME(3)` | 发货时间 |
| `received_at` | `DATETIME(3)` | 确认收货时间 |
| `completed_at` | `DATETIME(3)` | 完成时间 |
| `cancelled_at` | `DATETIME(3)` | 取消时间 |
| `cancel_reason` | `VARCHAR(255)` | 取消原因 |
| `created_at` | `DATETIME(3)` | 下单时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

订单状态：

```text
pending_payment    待付款
pending_shipment   待发货
pending_receipt    待收货
pending_review     待评价
completed          已完成
cancelled          已取消
refunding          退款中
refunded           退款成功
```

索引：

- `UNIQUE(order_no)`
- `INDEX(user_id, status, created_at)`
- `INDEX(status, payment_expire_at)`

### 8.2 `order_address_snapshot`

订单地址快照，防止用户修改地址后历史订单地址发生变化。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `receiver` | `VARCHAR(64)` | 收货人 |
| `phone` | `VARCHAR(20)` | 收货手机号 |
| `province` | `VARCHAR(64)` | 省 |
| `city` | `VARCHAR(64)` | 市 |
| `district` | `VARCHAR(64)` | 区县 |
| `detail` | `VARCHAR(255)` | 详细地址 |
| `created_at` | `DATETIME(3)` | 创建时间 |

约束：

- `UNIQUE(order_id)`

### 8.3 `order_item`

订单商品明细和商品快照。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID，可为空 |
| `seckill_activity_id` | `BIGINT UNSIGNED` | 秒杀活动，可为空 |
| `product_name` | `VARCHAR(255)` | 下单时商品名称 |
| `product_image` | `VARCHAR(512)` | 下单时商品图片 |
| `unit_price` | `DECIMAL(10,2)` | 下单时成交单价 |
| `quantity` | `INT UNSIGNED` | 购买数量 |
| `line_amount` | `DECIMAL(10,2)` | 商品行金额 |
| `shipping_fee` | `DECIMAL(10,2)` | 该商品订单运费 |
| `is_seckill` | `TINYINT` | 是否秒杀商品 |
| `created_at` | `DATETIME(3)` | 创建时间 |

索引：

- `INDEX(order_id)`
- `INDEX(product_id, created_at)`

### 8.4 `inventory_reservation`

下单后锁定库存，支付成功后转为已售，超时取消后释放。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `activity_id` | `BIGINT UNSIGNED` | 秒杀活动 ID，可为空 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `order_item_id` | `BIGINT UNSIGNED` | 订单商品 ID |
| `quantity` | `INT UNSIGNED` | 锁定数量 |
| `reservation_type` | `VARCHAR(20)` | `normal` 或 `seckill` |
| `status` | `VARCHAR(20)` | `locked`、`released`、`deducted` |
| `expire_at` | `DATETIME(3)` | 锁定过期时间 |
| `released_at` | `DATETIME(3)` | 释放时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

### 8.5 `order_status_history`

保存订单状态变化记录，便于审计和问题排查。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `from_status` | `VARCHAR(32)` | 原状态，可为空 |
| `to_status` | `VARCHAR(32)` | 新状态 |
| `operator_type` | `VARCHAR(20)` | `user`、`system` |
| `operator_id` | `BIGINT UNSIGNED` | 操作者，可为空 |
| `reason` | `VARCHAR(255)` | 变更原因 |
| `created_at` | `DATETIME(3)` | 变更时间 |

索引：

- `INDEX(order_id, created_at)`

### 8.6 `payment_transaction`

当前支持模拟支付，未来可直接扩展真实支付。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `payment_no` | `VARCHAR(64)` | 支付流水号，唯一 |
| `payment_method` | `VARCHAR(20)` | `wechat` 或 `alipay` |
| `amount` | `DECIMAL(10,2)` | 支付金额 |
| `status` | `VARCHAR(20)` | `pending`、`success`、`failed`、`cancelled`、`refunded` |
| `idempotency_key` | `VARCHAR(128)` | 幂等键，唯一 |
| `provider_transaction_no` | `VARCHAR(128)` | 第三方流水号，可为空 |
| `paid_at` | `DATETIME(3)` | 支付完成时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束和索引：

- `UNIQUE(payment_no)`
- `UNIQUE(idempotency_key)`
- `INDEX(order_id, created_at)`

### 8.7 `order_shipment`

保存订单发货和物流信息。当前前端展示较少，但待发货、待收货状态需要该表支撑。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `logistics_company` | `VARCHAR(64)` | 物流公司 |
| `tracking_no` | `VARCHAR(128)` | 运单号 |
| `status` | `VARCHAR(20)` | `shipped`、`delivered` |
| `shipped_at` | `DATETIME(3)` | 发货时间 |
| `delivered_at` | `DATETIME(3)` | 签收时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束和索引：

- `UNIQUE(tracking_no)`
- `INDEX(order_id)`

## 9. 售后与退款表

### 9.1 `after_sale_request`

对应订单详情中的“申请退款”和个人中心的退款售后列表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `after_sale_no` | `VARCHAR(64)` | 售后单号，唯一 |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `type` | `VARCHAR(20)` | `refund_only` 或 `return_refund` |
| `status` | `VARCHAR(20)` | `pending`、`approved`、`rejected`、`refunded`、`cancelled` |
| `reason` | `VARCHAR(128)` | 售后原因 |
| `description` | `VARCHAR(1000)` | 问题描述 |
| `requested_amount` | `DECIMAL(10,2)` | 申请金额 |
| `approved_amount` | `DECIMAL(10,2)` | 审核金额 |
| `applied_at` | `DATETIME(3)` | 申请时间 |
| `processed_at` | `DATETIME(3)` | 处理时间 |
| `closed_at` | `DATETIME(3)` | 关闭时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

### 9.2 `after_sale_item`

支持多商品订单只申请其中部分商品售后。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `after_sale_id` | `BIGINT UNSIGNED` | 售后单 ID |
| `order_item_id` | `BIGINT UNSIGNED` | 订单商品 ID |
| `quantity` | `INT UNSIGNED` | 售后数量 |
| `amount` | `DECIMAL(10,2)` | 售后金额 |
| `created_at` | `DATETIME(3)` | 创建时间 |

### 9.3 `refund_transaction`

保存退款流水，模拟退款和真实退款均可使用。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `after_sale_id` | `BIGINT UNSIGNED` | 售后单 ID |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `refund_no` | `VARCHAR(64)` | 退款流水号，唯一 |
| `amount` | `DECIMAL(10,2)` | 退款金额 |
| `status` | `VARCHAR(20)` | `pending`、`success`、`failed` |
| `payment_method` | `VARCHAR(20)` | 原支付方式 |
| `provider_refund_no` | `VARCHAR(128)` | 第三方退款号，可为空 |
| `refunded_at` | `DATETIME(3)` | 退款完成时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

## 10. 收藏、足迹与评价表

### 10.1 `user_favorite`

对应前端 `favorites: string[]`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `created_at` | `DATETIME(3)` | 收藏时间 |

约束和索引：

- `UNIQUE(user_id, product_id)`
- `INDEX(user_id, created_at)`

取消收藏时可以直接删除记录。

### 10.2 `user_browse_history`

对应前端 `Footprint`，保存用户每个商品的最近一次浏览记录。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `viewed_at` | `DATETIME(3)` | 最近浏览时间 |
| `price_at_view` | `DECIMAL(10,2)` | 浏览时有效售价 |
| `last_notified_price` | `DECIMAL(10,2)` | 最近提醒价格，可为空 |
| `price_drop_notified_at` | `DATETIME(3)` | 最近提醒时间，可为空 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束和索引：

- `UNIQUE(user_id, product_id)`
- `INDEX(user_id, viewed_at)`

前端的 `hasPriceDrop` 建议动态计算：

```text
当前有效售价 < price_at_view
```

### 10.3 `product_review`

对应前端 `Review`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `product_id` | `BIGINT UNSIGNED` | 商品 ID |
| `order_id` | `BIGINT UNSIGNED` | 订单 ID |
| `order_item_id` | `BIGINT UNSIGNED` | 订单商品 ID |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `content` | `TEXT` | 评价内容 |
| `quality_score` | `TINYINT UNSIGNED` | 质量评分，1-5 |
| `service_score` | `TINYINT UNSIGNED` | 服务评分，1-5 |
| `logistics_score` | `TINYINT UNSIGNED` | 物流评分，1-5 |
| `anonymous` | `TINYINT` | 是否匿名 |
| `status` | `VARCHAR(20)` | `visible`、`hidden`、`pending` |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

约束和索引：

- `UNIQUE(user_id, order_item_id)`
- `INDEX(product_id, status, created_at)`
- `INDEX(product_id, quality_score)`

评价规则：

- 只有确认收货后进入待评价状态的订单商品可以评价
- 一个用户对一个订单商品只能评价一次
- 评价成功后订单从 `pending_review` 变为 `completed`
- 匿名评价只隐藏展示昵称，仍然保留 `user_id`

### 10.4 `review_media`

保存评价图片和视频。当前前端只做本地预览，后续接入上传接口后使用该表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `review_id` | `BIGINT UNSIGNED` | 评价 ID |
| `media_type` | `VARCHAR(20)` | `image` 或 `video` |
| `media_url` | `VARCHAR(512)` | 媒体访问地址 |
| `object_key` | `VARCHAR(512)` | 对象存储 Key |
| `sort_order` | `INT` | 媒体顺序 |
| `created_at` | `DATETIME(3)` | 创建时间 |

评价媒体文件不直接存储到数据库。

## 11. 客服与搜索表

### 11.1 `service_session`

客服页面没有登录限制，需要兼容游客会话。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID，可为空 |
| `visitor_token` | `VARCHAR(128)` | 游客会话标识，可为空 |
| `status` | `VARCHAR(20)` | `active` 或 `closed` |
| `last_message_at` | `DATETIME(3)` | 最后消息时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `closed_at` | `DATETIME(3)` | 关闭时间，可为空 |

### 11.2 `service_message`

对应前端 `ChatMessage`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `session_id` | `BIGINT UNSIGNED` | 会话 ID |
| `sender_type` | `VARCHAR(20)` | `user`、`service`、`system` |
| `sender_id` | `BIGINT UNSIGNED` | 发送者 ID，可为空 |
| `message_type` | `VARCHAR(20)` | `text`、`product_card`、`order_card`、`faq` |
| `content` | `TEXT` | 消息文本 |
| `product_id` | `BIGINT UNSIGNED` | 商品卡片关联商品，可为空 |
| `order_id` | `BIGINT UNSIGNED` | 订单卡片关联订单，可为空 |
| `payload_json` | `JSON` | 卡片快照数据，可为空 |
| `read_at` | `DATETIME(3)` | 已读时间，可为空 |
| `created_at` | `DATETIME(3)` | 消息时间 |

商品卡片和订单卡片建议保存 `payload_json` 快照，避免商品下架或订单状态变化后历史消息无法展示。

索引：

- `INDEX(session_id, created_at, id)`

### 11.3 `service_faq`

对应当前客服页面中的常见问题。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `question` | `VARCHAR(255)` | 问题 |
| `answer` | `TEXT` | 自动回复内容 |
| `sort_order` | `INT` | 排序值 |
| `status` | `VARCHAR(20)` | `active`、`disabled` |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 修改时间 |

### 11.4 `user_search_history`

对应前端最近搜索功能。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 主键 |
| `user_id` | `BIGINT UNSIGNED` | 用户 ID |
| `keyword` | `VARCHAR(128)` | 搜索关键词 |
| `searched_at` | `DATETIME(3)` | 搜索时间 |

约束和索引：

- `UNIQUE(user_id, keyword)`
- `INDEX(user_id, searched_at)`

当前前端只保留最近 10 条，后端可以通过排序后删除旧记录实现相同效果。游客搜索历史继续放在浏览器本地。

## 12. Redis 设计

Redis 用于临时数据、限流、库存预扣和高频读取，不替代 MySQL 作为最终数据源。

| 用途 | Key | 数据 | TTL/说明 |
| --- | --- | --- | --- |
| 短信验证码 | `orange:auth:sms:code:{purpose}:{phone}` | 验证码摘要 | 300 秒 |
| 短信限流 | `orange:auth:sms:limit:{purpose}:{phone}` | 任意占位值 | 60 秒 |
| 图形验证码 | `orange:auth:captcha:{captchaId}` | 答案摘要 | 300 秒 |
| 登录会话 | `orange:auth:session:{tokenId}` | 用户 ID、设备信息 | 按 Token 有效期 |
| 秒杀库存 | `orange:seckill:stock:{activityId}` | 可售库存 | 活动结束后清理 |
| 秒杀限购 | `orange:seckill:limit:{activityId}:{userId}` | 已购买数量 | 活动周期内有效 |
| 浏览足迹排序 | `orange:footprint:zset:{userId}` | 商品 ID | 只保留最近 100 条 |
| 足迹详情 | `orange:footprint:meta:{userId}:{productId}` | 浏览时间、浏览价格 | 随足迹更新 |
| WebSocket 在线状态 | `orange:ws:online:{userId}` | 连接服务器信息 | 短 TTL，定时续期 |

### 12.1 足迹 ZSet

建议使用：

```text
ZSet key: orange:footprint:zset:{userId}
score: viewed_at 时间戳
member: product_id
```

每次浏览商品时：

1. 更新商品在 ZSet 中的时间分数
2. 更新 `orange:footprint:meta:{userId}:{productId}`
3. 删除排名 100 之后的记录
4. 通过队列或 Redis Stream 异步写入 `user_browse_history`

### 12.2 秒杀库存

秒杀扣库存需要使用 Redis Lua 脚本或等价原子操作，同时结合 MySQL 订单和库存锁定记录，不能只修改 Redis 数值。

## 13. 核心业务流程

### 13.1 创建订单

订单提交时建议在一个数据库事务中完成：

1. 校验用户、地址、商品状态和有效售价
2. 校验秒杀活动时间、秒杀价格和用户限购数量
3. 使用条件更新扣减可售库存，增加锁定库存
4. 创建 `orders`，状态为 `pending_payment`
5. 创建 `order_address_snapshot`
6. 创建 `order_item`，写入商品、价格和运费快照
7. 创建 `inventory_reservation`
8. 创建 `order_status_history`
9. 订单创建成功后删除或清理已结算购物车商品

订单支付截止时间为下单时间加 30 分钟。

### 13.2 支付成功

模拟支付成功时：

1. 创建或更新 `payment_transaction`
2. 检查支付幂等键，避免重复支付
3. 订单从 `pending_payment` 改为 `pending_shipment`
4. 库存从锁定状态转为已售状态
5. 更新商品销量和秒杀销量
6. 写入订单状态历史

### 13.3 订单超时取消

后端需要定时任务或延迟队列扫描：

```text
status = pending_payment
payment_expire_at < 当前时间
```

扫描到后，在事务中完成：

1. 订单改为 `cancelled`
2. 写入取消原因
3. 释放 `inventory_reservation`
4. 恢复普通库存或秒杀库存
5. 写入 `order_status_history`

不能只依赖前端倒计时，因为用户关闭浏览器后前端不会继续执行取消逻辑。

### 13.4 发货和收货

```text
pending_shipment -> pending_receipt -> pending_review -> completed
```

当前没有后台管理系统，可以通过 SQL 或后续内部工具维护发货信息和订单状态。

### 13.5 售后退款

1. 创建 `after_sale_request`
2. 创建 `after_sale_item`
3. 订单改为 `refunding`
4. 审核通过后创建 `refund_transaction`
5. 退款成功后订单改为 `refunded`
6. 写入订单状态历史

## 14. 关键索引和约束汇总

必须存在的唯一约束：

```text
user_account.phone
product_category.code
product_inventory.product_id
seckill_user_purchase(activity_id, user_id)
cart_item(user_id, product_id)
orders.order_no
order_address_snapshot.order_id
payment_transaction.payment_no
payment_transaction.idempotency_key
user_favorite(user_id, product_id)
user_browse_history(user_id, product_id)
product_review(user_id, order_item_id)
```

商品列表接口建议支持以下参数：

```text
keyword
categoryId
seckillOnly
sort=default|price_asc|sales_desc
page
pageSize
```

价格排序和销量排序必须在数据库分页之前执行。否则无限滚动时只对当前已加载页面排序，会导致整体排序不准确。

评价列表接口建议支持：

```text
productId
filter=all|good|medium|bad|media
page
pageSize
```

`media` 筛选应通过 `review_media` 判断，不要只依赖评价表中的图片数量字段。

## 15. 当前不需要创建的表

根据当前需求，暂时不需要：

- 用户密码表
- 后台管理员表
- 商家表
- 优惠券表
- 发票表
- SKU 表
- 钱包账户表
- 积分表
- 真实支付渠道配置表
- Elasticsearch 商品索引表

## 16. 可后置扩展的表

### 16.1 商品标签表

如果以后需要标签筛选，增加：

```text
product_tag
- id
- name
- sort_order
- status

product_tag_rel
- product_id
- tag_id
```

### 16.2 价格提醒订阅表

当前前端只展示“降价了”标识，没有主动订阅和通知开关，因此暂时不需要。以后如果支持用户主动设置降价提醒，可以增加：

```text
user_price_alert
- id
- user_id
- product_id
- target_price
- status
- notified_at
- created_at
```

### 16.3 退货物流表

如果以后支持退货退款，再增加：

```text
after_sale_return_logistics
- id
- after_sale_id
- logistics_company
- tracking_no
- shipped_at
- received_at
```

## 17. 需要特别注意的前端与后端差异

### 17.1 商品详情和评价数据

当前前端部分页面直接读取 Mock 商品和评价数据。接入后端后，商品详情、评价列表和评价统计应全部以 API 返回为准。

### 17.2 购物车中的秒杀价格

当前前端购物车主要按照普通商品价格计算。后端在结算时必须重新校验：

- 秒杀活动是否仍在进行
- 秒杀价格是否有效
- 秒杀库存是否充足
- 用户是否超过限购数量

订单最终金额以服务端计算结果为准。

### 17.3 运费规则

当前前端逻辑是每个选中的商品收取一次该商品运费，同一商品增加数量不会重复计算运费。订单创建后，应将最终运费保存到 `order_item.shipping_fee` 和 `orders.shipping_amount`。

如果未来改成按件计费，需要同步调整结算逻辑和金额计算规则。

### 17.4 评价统计

`product.rating_avg` 和 `product.review_count` 是商品列表展示的冗余统计字段。评价新增、隐藏或删除后，需要在事务或异步任务中重新计算，不能长期依赖旧值。

### 17.5 数据安全

- 手机号和收货手机号在接口返回时需要脱敏
- 验证码只保存摘要，不保存明文
- 不要把支付敏感信息写入日志
- 订单、支付、库存接口必须校验当前用户是否拥有对应数据
- 评价、收藏、地址和客服会话查询必须带用户条件
