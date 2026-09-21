# 橙子商城后端接口文档

## 1. 文档说明

本文档对应数据库脚本 `sql/create_core_business_tables.sql`。脚本创建数据库 `orange_market_simple`，当前版本只围绕 C 端商城的核心流程设计：

- 手机号 + 短信验证码登录，首次登录自动创建用户
- 分类和商品浏览
- 游客本地购物车，登录后使用服务端购物车
- 收货地址管理
- 收藏、浏览足迹和搜索历史
- 创建订单、支付、确认收货
- 商品评价

当前数据库共 11 张表：

- `user_account`
- `product_category`
- `product`
- `cart_item`
- `user_address`
- `orders`
- `order_item`
- `product_review`
- `user_favorite`
- `user_browse_history`
- `user_search_history`

本版本暂不设计秒杀、优惠券、发票、SKU、售后退款、真实支付、独立物流、客服和媒体附件功能。

本文档描述接口契约和业务规则，不代表所有接口已经完成 Controller、Service 和 Mapper 实现。

## 2. 全局约定

### 2.1 基础信息

- Base URL：`/api`
- 数据格式：`application/json`
- 字符集：UTF-8
- 时间格式：ISO 8601，例如 `2026-09-05T12:30:00.000Z`
- ID：数据库使用 `BIGINT`，接口统一返回字符串
- 金额：接口返回数字并保留两位小数，数据库使用 `DECIMAL(10,2)`
- 分页页码从 `1` 开始
- 默认 `pageSize` 为 `12`，最大为 `50`

### 2.2 请求头

登录后请求携带：

```http
Authorization: Bearer {token}
Content-Type: application/json
```

本版本没有支付流水表和订单幂等记录表。订单创建时建议仍然携带客户端生成的请求 ID，服务端是否实现幂等由业务层决定：

```http
X-Request-Id: {request-id}
```

### 2.3 统一成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1788601800000
}
```

### 2.4 统一失败响应

```json
{
  "code": 40000,
  "message": "请求参数错误",
  "data": null,
  "timestamp": 1788601800000
}
```

常用错误码：

- `40000`：请求参数错误
- `40001`：短信验证码错误或已过期
- `40002`：手机号格式错误
- `40003`：短信发送过于频繁
- `40100`：未登录或 Token 无效
- `40300`：无权访问该资源
- `40400`：资源不存在
- `40900`：库存不足、重复操作或数据冲突
- `42200`：当前业务状态不允许操作
- `42900`：请求频率过高
- `50000`：服务端异常

### 2.5 分页响应

商品、评价、收藏、足迹和订单列表统一返回：

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 12,
  "hasMore": false
}
```

## 3. 数据库与接口模型

### 3.1 用户 `user_account`

数据库保存手机号、昵称、头像、性别、生日、状态和角色。验证码、登录 Token 和登录会话不落 MySQL，使用 Redis 保存。

接口中的用户对象：

```json
{
  "id": "10001",
  "phone": "13800138001",
  "nickname": "橙子同学",
  "avatarUrl": "https://example.com/avatar.png",
  "gender": 0,
  "birthday": "1998-08-18",
  "status": "active",
  "role": "USER",
  "lastLoginAt": "2026-09-05T12:30:00.000Z"
}
```

性别取值：`0` 保密、`1` 男、`2` 女。`deletedAt`、`createdAt` 和 `updatedAt` 默认作为数据库内部字段，不要求所有接口返回。

### 3.2 分类 `product_category`

数据库字段 `icon_key` 对外转换为 `icon`，`is_virtual` 对外转换为 `isVirtual`。

```json
{
  "id": "10001",
  "name": "数码",
  "eyebrow": "SMART LIFE",
  "color": "#e7f5ff",
  "icon": "Laptop",
  "isVirtual": false
}
```

当前初始化数据全部为普通分类，`isVirtual` 为 `false`。分类接口只返回 `status=active` 且未逻辑删除的数据。

### 3.3 商品 `product`

数据库和接口字段映射：

- `sale_price` -> `price`
- `cover_image` 和 `images_json` -> `images`
- `sales_count` -> `sales`
- `rating_avg` -> `rating`
- `tags_json` -> `tags`
- `stock` -> `stock`

商品对象：

```json
{
  "id": "20001",
  "name": "晨雾白跑鞋",
  "subtitle": "轻盈缓震，通勤与运动都舒适",
  "categoryId": "10006",
  "images": [
    "https://example.com/product-cover.jpg"
  ],
  "videoUrl": null,
  "price": 239.00,
  "originalPrice": 399.00,
  "stock": 120,
  "sales": 8420,
  "rating": 4.90,
  "reviewCount": 86,
  "description": "商品详细描述",
  "shippingFee": 8.00,
  "tags": ["热卖", "舒适"]
}
```

说明：

- `images_json` 保存图片 URL 数组；为空时可以使用 `cover_image` 组装单元素数组
- `tags_json` 保存标签字符串数组
- 当前没有 SKU，一个商品对应一个价格和一个库存值
- `status` 取值为 `draft`、`on_sale`、`off_sale`
- 面向用户的商品列表和详情只返回 `on_sale` 商品

### 3.4 购物车 `cart_item`

数据库只保存用户、商品、数量和选中状态。商品名称、图片、当前价格和运费由接口查询 `product` 后组装，不重复保存。

```json
{
  "id": "40001",
  "productId": "20001",
  "quantity": 2,
  "selected": true,
  "product": {},
  "effectivePrice": 239.00,
  "shippingFee": 8.00
}
```

同一用户和同一商品只能有一条购物车记录。游客购物车由前端本地保存，登录后通过合并接口写入数据库。

### 3.5 地址 `user_address`

```json
{
  "id": "30001",
  "receiver": "橙子同学",
  "phone": "138****8001",
  "provinceCode": "310000",
  "province": "上海市",
  "cityCode": "310100",
  "city": "上海市",
  "districtCode": "310115",
  "district": "浦东新区",
  "detail": "世纪大道 100 号橙子大厦 8 楼",
  "isDefault": true
}
```

数据库保存完整手机号，接口返回时脱敏。省、市、区编码可以为空，但名称不能为空。

### 3.6 订单 `orders` 和订单商品 `order_item`

新数据库将收货地址快照直接保存到 `orders`，不再单独创建订单地址表。订单商品名称、图片、单价和行金额保存到 `order_item`，用于保证历史订单不受商品修改影响。

订单对象：

```json
{
  "id": "60001",
  "orderNo": "OM202609050001",
  "status": "pending_payment",
  "items": [],
  "address": {},
  "subtotal": 239.00,
  "shippingFee": 8.00,
  "total": 247.00,
  "buyerRemark": "请尽快发货",
  "paymentMethod": null,
  "trackingNo": null,
  "createdAt": "2026-09-05T12:30:00.000Z",
  "paymentExpireAt": "2026-09-05T13:00:00.000Z",
  "paidAt": null,
  "shippedAt": null,
  "receivedAt": null,
  "completedAt": null,
  "cancelledAt": null
}
```

订单状态：

- `pending_payment`：待付款
- `pending_shipment`：待发货
- `pending_receipt`：待收货
- `pending_review`：待评价
- `completed`：已完成
- `cancelled`：已取消

订单明细对象：

```json
{
  "id": "61001",
  "productId": "20001",
  "productName": "晨雾白跑鞋",
  "productImage": "https://example.com/product-cover.jpg",
  "unitPrice": 239.00,
  "quantity": 1,
  "lineAmount": 239.00
}
```

### 3.7 评价 `product_review`

当前评价只使用一个 `rating` 字段，不拆分质量、服务和物流评分，也不支持评价媒体。

```json
{
  "id": "50001",
  "productId": "20001",
  "userName": "小橙子",
  "avatar": "https://example.com/avatar.png",
  "content": "质感比预期更好，物流也很快。",
  "rating": 5,
  "anonymous": false,
  "createdAt": "2026-09-05T12:30:00.000Z"
}
```

匿名评价返回 `userName=匿名用户`，并且不返回真实头像。`rating` 范围为 `1` 到 `5`。

评价汇总对象：

```json
{
  "average": 4.90,
  "reviewCount": 86,
  "goodRate": 0.98,
  "allCount": 86,
  "goodCount": 82,
  "mediumCount": 3,
  "badCount": 1
}
```

### 3.8 用户行为表

- `user_favorite`：唯一约束为 `(user_id, product_id)`，重复收藏应保持幂等
- `user_browse_history`：唯一约束为 `(user_id, product_id)`，重复浏览更新 `viewed_at`
- `user_search_history`：唯一约束为 `(user_id, keyword)`，重复搜索更新 `searched_at`

足迹接口不再返回 `priceAtView`、`hasPriceDrop` 等价格提醒字段。

## 4. 认证接口

### 4.1 获取图形验证码

```http
GET /api/auth/captcha
```

是否需要登录：否。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "image": "data:image/png;base64,...",
    "captchaKey": "captcha-abc123"
  },
  "timestamp": 1788601800000
}
```

图形验证码答案只保存到 Redis，不写入数据库。

### 4.2 发送短信验证码

```http
POST /api/auth/sms/send
```

是否需要登录：否。

请求体：

```json
{
  "phone": "13800138001",
  "purpose": "login",
  "captchaKey": "captcha-abc123",
  "captchaCode": "7K3M"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "cooldown": 60,
    "expiresIn": 300
  },
  "timestamp": 1788601800000
}
```

开发阶段短信验证码固定为 `1234`，并且仍然需要校验手机号、图形验证码和发送频率。短信验证码只保存到 Redis，不创建短信日志表。

### 4.3 手机号登录或注册

```http
POST /api/auth/login
```

是否需要登录：否。

请求体：

```json
{
  "phone": "13800138001",
  "smsCode": "1234"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "token-value",
    "expiresAt": "2026-09-06T12:30:00.000Z",
    "isNewUser": true
  },
  "timestamp": 1788601800000
}
```

手机号不存在时创建 `user_account`，默认昵称为手机号后四位拼接生成的昵称，默认角色为 `USER`。手机号已存在时直接登录。

登录会话保存到 Redis，Token 失效或退出登录后不能继续访问需要认证的接口。

### 4.4 获取当前用户

```http
GET /api/auth/me
```

是否需要登录：是。

响应 `data` 为当前用户对象，字段参见 [3.1 用户](#31-用户-user_account)。

### 4.5 退出登录

```http
DELETE /api/auth/logout
```

是否需要登录：是。

服务端删除当前 Redis 登录会话。

## 5. 分类与商品接口

### 5.1 获取分类

```http
GET /api/categories
```

是否需要登录：否。

响应 `data` 为分类对象数组，字段参见 [3.2 分类](#32-分类-product_category)。

### 5.2 获取商品列表

```http
GET /api/products
```

是否需要登录：否。

查询参数：

- `page`：整数，默认 `1`
- `pageSize`：整数，默认 `12`，最大 `50`
- `keyword`：可选，搜索商品名称、副标题和描述
- `categoryId`：可选，分类 ID
- `sort`：可选，`default`、`price_asc`、`sales_desc`

示例：

```http
GET /api/products?page=1&pageSize=12&keyword=跑鞋&categoryId=10006&sort=sales_desc
```

响应 `data`：

```json
{
  "list": [
    {
      "id": "20001",
      "name": "晨雾白跑鞋",
      "price": 239.00,
      "originalPrice": 399.00,
      "stock": 120,
      "sales": 8420,
      "rating": 4.90,
      "reviewCount": 86,
      "images": ["https://example.com/product-cover.jpg"],
      "tags": ["热卖", "舒适"]
    }
  ],
  "total": 1,
  "page": 1,
  "pageSize": 12,
  "hasMore": false
}
```

排序必须在分页前执行。默认只查询 `status=on_sale` 且未逻辑删除的商品。

### 5.3 获取商品详情

```http
GET /api/products/{productId}
```

是否需要登录：否。

响应 `data`：

```json
{
  "product": {},
  "reviews": [],
  "reviewSummary": {
    "average": 4.90,
    "reviewCount": 86,
    "goodRate": 0.98,
    "allCount": 86,
    "goodCount": 82,
    "mediumCount": 3,
    "badCount": 1
  }
}
```

`reviews` 为详情页预览评价，可以只返回少量最新评价。商品不存在或未上架时返回 `40400`。

### 5.4 获取商品评价

```http
GET /api/products/{productId}/reviews
```

是否需要登录：否。

查询参数：

- `filter`：`all`、`good`、`medium`、`bad`，默认 `all`
- `page`：默认 `1`
- `pageSize`：默认 `10`，最大 `50`
- `sort`：当前支持 `latest`，默认 `latest`

筛选规则：

- `good`：`rating >= 4`
- `medium`：`rating = 3`
- `bad`：`rating <= 2`

本版本没有评价媒体表，因此不提供 `media` 筛选。

响应 `data`：

```json
{
  "list": [],
  "total": 86,
  "page": 1,
  "pageSize": 10,
  "hasMore": true,
  "summary": {}
}
```

## 6. 购物车接口

游客购物车由前端保存在浏览器本地。登录后调用合并接口，将本地商品同步到 `cart_item`。

### 6.1 获取购物车

```http
GET /api/cart
```

是否需要登录：是。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "selectedCount": 2,
    "subtotal": 717.00,
    "shippingFee": 8.00,
    "total": 725.00
  },
  "timestamp": 1788601800000
}
```

金额和商品展示信息由当前商品数据计算，不能信任客户端传入的价格。

### 6.2 添加购物车商品

```http
POST /api/cart/items
```

是否需要登录：是。

请求体：

```json
{
  "productId": "20001",
  "quantity": 1
}
```

同一用户重复添加同一商品时增加原记录数量，不创建第二条记录。商品必须在售且库存充足。

### 6.3 修改购物车商品

```http
PATCH /api/cart/items/{cartItemId}
```

是否需要登录：是。

请求体中的字段可以单独传递：

```json
{
  "quantity": 2,
  "selected": true
}
```

`quantity` 必须大于等于 `1`，不能超过商品当前库存。

### 6.4 删除购物车商品

```http
DELETE /api/cart/items/{cartItemId}
```

### 6.5 全选或取消全选

```http
PUT /api/cart/items/selection
```

请求体：

```json
{
  "selected": true
}
```

### 6.6 删除已选商品

```http
DELETE /api/cart/items/selected
```

### 6.7 合并游客购物车

```http
POST /api/cart/merge
```

请求体：

```json
{
  "items": [
    {
      "productId": "20001",
      "quantity": 1,
      "selected": true
    }
  ]
}
```

服务端按商品 ID 合并数量，并重新校验商品状态和库存。

## 7. 用户资料、地址和用户行为接口

### 7.1 获取用户资料

```http
GET /api/users/me
```

是否需要登录：是。

返回当前用户对象。该接口可以与 `/api/auth/me` 返回相同的数据。

### 7.2 修改用户资料

```http
PATCH /api/users/me
```

是否需要登录：是。

请求体：

```json
{
  "nickname": "橙子同学",
  "gender": 0,
  "birthday": "1998-08-18",
  "avatarUrl": "https://cdn.example.com/avatars/10001/abc.png"
}
```

当前版本允许修改昵称、性别、生日和头像。`avatarUrl` 必须属于本项目 OSS（或 CDN）且路径为 `avatars/{当前用户ID}/`。推荐流程：先调用 `POST /api/uploads/sign`，前端 PostObject 直传，OSS 回调成功后资料会自动更新；本地没有公网回调时，也可在直传成功后把返回的 `accessUrl` 通过本接口写入。

### 7.3 获取地址列表

```http
GET /api/users/me/addresses
```

是否需要登录：是。

按创建时间倒序返回当前用户未删除的地址。

### 7.4 新增地址

```http
POST /api/users/me/addresses
```

请求体：

```json
{
  "receiver": "橙子同学",
  "phone": "13800138001",
  "provinceCode": "310000",
  "province": "上海市",
  "cityCode": "310100",
  "city": "上海市",
  "districtCode": "310115",
  "district": "浦东新区",
  "detail": "世纪大道 100 号橙子大厦 8 楼",
  "isDefault": true
}
```

如果设置为默认地址，服务端在同一个事务中取消该用户的其他默认地址。

### 7.5 修改地址

```http
PUT /api/users/me/addresses/{addressId}
```

请求体与新增地址相同。只能修改当前用户自己的地址。

### 7.6 删除地址

```http
DELETE /api/users/me/addresses/{addressId}
```

使用逻辑删除。删除默认地址后，可以将该用户最近创建的其他地址设置为默认地址。

### 7.7 设置默认地址

```http
PUT /api/users/me/addresses/{addressId}/default
```

必须在事务中取消旧默认地址，再设置新默认地址。

### 7.8 获取收藏夹

```http
GET /api/users/me/favorites?page=1&pageSize=20
```

按收藏时间倒序返回收藏记录，并组装商品展示信息。

### 7.9 收藏商品

```http
POST /api/users/me/favorites
```

请求体：

```json
{
  "productId": "20001"
}
```

重复收藏建议直接返回成功，不重复插入。

### 7.10 取消收藏

```http
DELETE /api/users/me/favorites/{productId}
```

### 7.11 获取浏览足迹

```http
GET /api/users/me/browse-history?page=1&pageSize=20
```

响应中的每条记录：

```json
{
  "id": "80001",
  "productId": "20001",
  "viewedAt": "2026-09-05T12:30:00.000Z",
  "product": {}
}
```

前端可以按照 `viewedAt` 分组为“今天、昨天、近 7 天、更早”。

### 7.12 写入浏览足迹

```http
POST /api/users/me/browse-history
```

请求体：

```json
{
  "productId": "20001"
}
```

重复浏览同一商品时更新原记录的 `viewed_at`，不新增记录。服务端只接受存在的商品 ID。

### 7.13 删除单条浏览足迹

```http
DELETE /api/users/me/browse-history/{historyId}
```

### 7.14 清空浏览足迹

```http
DELETE /api/users/me/browse-history
```

### 7.15 批量删除浏览足迹

```http
POST /api/users/me/browse-history/batch-delete
```

请求体：

```json
{
  "ids": ["80001", "80002"]
}
```

### 7.16 获取搜索历史

```http
GET /api/users/me/search-history?page=1&pageSize=10
```

按 `searchedAt` 倒序返回最近搜索关键词。

### 7.17 写入搜索关键词

```http
POST /api/users/me/search-history
```

请求体：

```json
{
  "keyword": "跑鞋"
}
```

关键词去除首尾空格后保存。相同用户再次搜索相同关键词时更新 `searched_at`。

### 7.18 删除单条搜索历史

```http
DELETE /api/users/me/search-history/{historyId}
```

### 7.19 清空搜索历史

```http
DELETE /api/users/me/search-history
```

游客搜索历史继续由前端本地保存。

## 8. 订单与结算接口

### 8.1 购物车结算预览

```http
POST /api/orders/cart/preview
```

是否需要登录：是。

请求体：

```json
{
  "cartItemIds": ["40001", "40002"],
  "addressId": "30001"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "address": {},
    "subtotal": 717.00,
    "shippingFee": 8.00,
    "total": 725.00,
    "paymentExpireMinutes": 30
  },
  "timestamp": 1788601800000
}
```

预览时重新校验商品是否在售、库存是否充足、购物车记录是否属于当前用户，并由服务端重新计算金额。

### 8.2 购物车创建订单

```http
POST /api/orders/cart
```

是否需要登录：是。

请求体：

```json
{
  "cartItemIds": ["40001", "40002"],
  "addressId": "30001",
  "buyerRemark": "请尽快发货"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "订单创建成功",
  "data": {
    "orderId": "60001",
    "orderNo": "OM202609050001",
    "status": "pending_payment",
    "total": 725.00,
    "paymentExpireAt": "2026-09-05T13:00:00.000Z"
  },
  "timestamp": 1788601800000
}
```

订单创建事务完成以下操作：

1. 校验当前用户的购物车和地址
2. 创建 `orders`，写入收货地址快照
3. 创建 `order_item`，写入商品名称、图片、单价和数量快照
4. 使用条件更新扣减 `product.stock`
5. 删除或清理已购买的购物车记录

库存扣减失败时整个事务回滚，并返回 `40900`。

### 8.3 直接购买创建订单

```http
POST /api/orders/direct
```

是否需要登录：是。

请求体：

```json
{
  "productId": "20001",
  "quantity": 1,
  "addressId": "30001",
  "buyerRemark": "请尽快发货"
}
```

成功响应结构与 8.2 相同。订单创建事务完成以下操作：

1. 校验当前用户的商品、库存和地址
2. 创建 `orders`，写入收货地址快照
3. 创建 `order_item`，写入商品名称、图片、单价和数量快照
4. 使用条件更新扣减 `product.stock`

直接购买不会新增、修改或删除购物车记录。库存扣减失败时整个事务回滚，并返回 `40900`。

### 8.4 获取订单列表

```http
GET /api/orders?status=pending_payment&page=1&pageSize=10
```

是否需要登录：是。

查询参数：

- `status`：可选，不传表示全部订单
- `page`：默认 `1`
- `pageSize`：默认 `10`，最大 `50`

只能查询当前登录用户自己的订单。

### 8.5 获取订单详情

```http
GET /api/orders/{orderId}
```

是否需要登录：是，只能查看自己的订单。

响应 `data` 为完整订单对象，包含 `items`、地址快照、金额、支付状态和 `trackingNo`。

本版本没有独立物流表，物流信息直接读取 `orders.tracking_no` 和 `orders.shipped_at`。

### 8.6 取消订单

```http
POST /api/orders/{orderId}/cancel
```

请求体：

```json
{
  "reason": "不想要了"
}
```

只允许取消 `pending_payment` 订单。取消时将订单状态改为 `cancelled`，并将订单商品数量加回 `product.stock`。

### 8.7 确认收货

```http
POST /api/orders/{orderId}/receive
```

只允许操作 `pending_receipt` 订单。成功后状态改为 `pending_review`，写入 `received_at`。

## 9. 支付接口

支付流水写入 `payment_transaction`。确认付款以支付宝异步通知为准；本地无公网回调时可用查单接口兜底。

### 9.1 发起支付

```http
POST /api/orders/{orderId}/pay
```

是否需要登录：是。

请求体：

```json
{
  "paymentMethod": "alipay"
}
```

`paymentMethod` 允许：

- `alipay`：当面付预下单（`alipay.trade.precreate`），密钥模式 RSA2 加签，`biz_content` 使用开放平台 AES 密钥加密。订单仍为 `pending_payment`，响应带 `qrCode`，前端自行展示二维码。
- `mock`：仅当 `alipay.allow-mock=true` 时可用，同步把订单改为 `pending_shipment`。

其它值返回 `40000`。仅 `pending_payment` 且未超过 `paymentExpireAt` 可支付。同一订单若已有未过期的 pending 支付宝流水，直接返回已有 `qrCode`，不重复预下单。

支付宝预下单响应 `data`：

```json
{
  "orderId": "60001",
  "orderNo": "OM202609050001",
  "status": "pending_payment",
  "paymentMethod": "alipay",
  "paidAt": null,
  "qrCode": "https://qr.alipay.com/baxxxx",
  "outTradeNo": "P60001T1758440000ABCD1234",
  "expireAt": "2026-09-05T13:00:00.000Z"
}
```

mock 成功时 `status` 为 `pending_shipment`，写入 `paidAt`，`qrCode` 为空。

### 9.2 主动查单

```http
POST /api/orders/{orderId}/payment/sync
```

是否需要登录：是。无请求体。

调用加密的 `alipay.trade.query`。若支付宝为 `TRADE_SUCCESS` / `TRADE_FINISHED`，与异步通知同一套落库（幂等）。用于本地没有公网 `notify_url`、或回调延迟时确认付款。

### 9.3 支付宝异步通知

```http
POST /api/payments/alipay/notify
```

是否需要登录：否。支付宝服务器 POST 表单。验签使用支付宝公钥 RSA2。校验 `app_id`、`out_trade_no`、`total_amount`。仅 `TRADE_SUCCESS` / `TRADE_FINISHED` 记成功。

**响应必须是纯文本 `success` 或 `fail`**，不能包 JSON。金额不一致时不改订单，仍返回 `success` 避免无限重试。

支付成功后：流水 `success`，回填 `trade_no`（支付宝交易号，预下单时为空），订单 `pending_payment` → `pending_shipment`。

### 9.4 基于消息队列的支付超时取消

支付超时取消通过 RabbitMQ 的 TTL 队列和死信交换机实现，不使用定时任务轮询订单表。

订单创建成功后，服务端发送一条包含 `orderId` 的支付超时消息：

```java
rabbitTemplate.convertAndSend(
        "order.payment.timeout.exchange",
        "order.payment.timeout",
        orderId);
```

消息流转如下：

```text
order.payment.timeout.exchange
        |
        | routingKey = order.payment.timeout
        v
order.payment.timeout.queue
        |
        | TTL = 30 分钟，消息过期
        v
order.cancel.exchange
        |
        | routingKey = order.cancel
        v
order.cancel.queue
        |
        v
OrderTimeoutConsumer
```

RabbitMQ 资源说明：

- `order.payment.timeout.exchange`：支付超时消息的直接交换机，负责将消息路由到等待队列。
- `order.payment.timeout.queue`：持久化等待队列，消息最多等待 30 分钟；消息过期后转发到 `order.cancel.exchange`。
- `order.cancel.exchange`：死信交换机，负责将过期消息路由到取消队列。
- `order.cancel.queue`：持久化取消队列，由订单超时消费者监听。

消费者监听 `order.cancel.queue`，收到订单 ID 后：

1. 若订单仍为 `pending_payment` 且存在 pending 支付宝流水，先加密查单。
2. 支付宝已支付则落库为待发货，**不取消**。
3. 未支付则加密关单；关单发现已支付同样落库，不取消。
4. 仍未支付时将流水改为 `cancelled`，再把订单改为 `cancelled` 并恢复库存。

如果订单已经支付、已经手动取消或消息重复投递，消费者直接结束处理。前端倒计时只用于展示，不能作为实际取消依据。用户主动取消待付款订单时同样会先查单/关单。

## 10. 评价接口

### 10.1 获取待评价订单商品

```http
GET /api/users/me/reviews/pending
```

是否需要登录：是。

查询 `pending_review` 订单中的 `order_item`，排除已经存在于 `product_review` 的订单商品。

响应 `data` 为待评价商品列表：

```json
[
  {
    "orderId": "60001",
    "orderNo": "OM202609050001",
    "orderItemId": "61001",
    "productId": "20001",
    "productName": "晨雾白跑鞋",
    "productImage": "https://example.com/product-cover.jpg",
    "unitPrice": 239.00,
    "quantity": 1,
    "lineAmount": 239.00
  }
]
```

### 10.2 提交订单评价

```http
POST /api/orders/{orderId}/reviews
```

是否需要登录：是。

请求体：

```json
{
  "reviews": [
    {
      "orderItemId": "61001",
      "productId": "20001",
      "rating": 5,
      "content": "商品很好，物流也很快。",
      "anonymous": false
    }
  ]
}
```

服务端必须校验：

- 订单属于当前用户
- 订单商品属于当前订单
- `productId` 与订单商品一致
- 评分范围为 `1` 到 `5`
- 同一用户不能重复评价同一订单商品

评价成功后更新商品的 `rating_avg` 和 `review_count`。订单所有商品都完成评价后，可以将订单状态更新为 `completed`，并写入 `completed_at`。

成功响应 `data`：

```json
{
  "orderId": "60001",
  "orderNo": "OM202609050001",
  "status": "completed",
  "reviewIds": ["50001"],
  "completedAt": "2026-09-05T12:40:00.000Z"
}
```

本版本不提供评价图片或视频上传接口。

## 11. 接口权限总览

- 游客：图形验证码、短信发送、登录、分类、商品列表、商品详情、商品评价列表
- 登录用户：当前用户、购物车、地址、收藏、浏览足迹、搜索历史、结算、订单、支付、确认收货、提交评价、OSS 签名直传（头像）
- 管理员：待发货订单列表、发货、封禁用户、更新商品图片、签发商品图上传
- 游客购物车、搜索历史：由前端本地保存
- 商品、分类数据：分类与商品主数据仍通过 SQL 维护；商品图片可通过管理端接口更新

## 12. 管理端接口

管理端接口仍使用 Bearer Token 鉴权，并额外要求当前用户的 `role` 为 `admin` 或 `ADMIN` 且 `status=active`。管理员账号不单独建表，直接在 `user_account` 中维护。

### 12.1 待发货订单列表

```http
GET /api/admin/orders?status=pending_shipment&page=1&pageSize=10
Authorization: Bearer <admin-token>
```

查询参数：

- `status`：可选，默认 `pending_shipment`；非法值返回 `40000 订单状态参数错误`
- `page`：默认 `1`
- `pageSize`：默认 `10`，最大 `50`

与用户端订单列表不同，该接口查询全站订单，不按 `userId` 过滤。响应结构与 `GET /api/orders` 相同（`list` / `total` / `page` / `pageSize` / `hasMore`），每条订单含商品行、收货地址快照、金额和 `paidAt`。

### 12.2 管理员发货

```http
POST /api/admin/orders/{orderId}/ship
Authorization: Bearer <admin-token>
Content-Type: application/json
```

请求体：

```json
{
  "trackingNo": "SF1234567890"
}
```

服务端只允许 `pending_shipment` 订单发货，并在一个事务中将订单更新为 `pending_receipt`，同时写入 `tracking_no` 和 `shipped_at`。

成功响应 `data`：

```json
{
  "orderId": "60001",
  "orderNo": "OM202609050001",
  "status": "pending_receipt",
  "trackingNo": "SF1234567890",
  "shippedAt": "2026-09-05T13:00:00.000Z"
}
```

订单不存在返回 `40400`，订单不是待发货状态返回 `42200`，物流单号为空或超过 128 个字符返回 `40000`。

### 12.3 封禁用户

```http
PUT /api/admin/users/{userId}/ban
Authorization: Bearer <admin-token>
```

该接口不需要请求体。服务端将目标用户的 `user_account.status` 更新为 `disabled`，删除该用户的 Redis 登录会话，并阻止其再次通过短信登录。重复封禁已封禁用户可以安全返回成功，但不能封禁管理员账号。

成功响应 `data`：

```json
{
  "userId": "10002",
  "status": "disabled",
  "role": "USER"
}
```

非管理员访问管理端接口返回 `40300`。

### 12.4 更新商品图片

```http
PUT /api/admin/products/{productId}/images
Authorization: Bearer <admin-token>
Content-Type: application/json
```

请求体：

```json
{
  "coverImage": "https://cdn.example.com/products/20260919/cover.png",
  "images": [
    "https://cdn.example.com/products/20260919/cover.png",
    "https://cdn.example.com/products/20260919/detail.png"
  ]
}
```

管理员先通过 `POST /api/uploads/sign`（`scene=product`）拿到 PostPolicy，再 PostObject 直传到 OSS。本接口把已上传的 URL 写入 `cover_image` 和 `images_json`。`images` 与 `coverImage` 至少提供一项；每个 URL 必须属于本存储空间且路径前缀为 `products/`。未传 `coverImage` 时使用 `images[0]`。最多 20 张。商品不存在返回 `40400`。

## 13. 对象存储上传

对齐阿里云「服务端签名直传并设置上传回调」：STS 临时凭证 + PostPolicy（OSS4-HMAC-SHA256）+ 浏览器 PostObject + 服务端回调。

### 14.1 签发 PostPolicy

```http
POST /api/uploads/sign
Authorization: Bearer <token>
Content-Type: application/json
```

请求体：

```json
{
  "scene": "avatar",
  "filename": "avatar.png",
  "contentType": "image/png"
}
```

`scene` 取值：`avatar`（任意登录用户，对象键前缀 `avatars/{userId}/`）、`product`（仅管理员，前缀 `products/{yyyyMMdd}/`）。仅允许 `image/jpeg`、`image/png`、`image/webp`、`image/gif`。头像不超过 2MB，商品图不超过 5MB（由 Policy 的 `content-length-range` 限制）。对象键由服务端生成，客户端不能指定。

成功响应 `data`：

```json
{
  "dir": "avatars/10001/",
  "key": "avatars/10001/ab12cd.png",
  "host": "https://bucket.oss-cn-hangzhou.aliyuncs.com",
  "accessUrl": "https://cdn.example.com/avatars/10001/ab12cd.png",
  "policy": "eyJl****",
  "security_token": "CAIS****",
  "signature": "9103****",
  "x_oss_credential": "STS.xxx/20241127/cn-hangzhou/oss/aliyun_v4_request",
  "x_oss_date": "20241127T060941Z",
  "x_oss_signature_version": "OSS4-HMAC-SHA256",
  "callback": "eyJjYWxs****"
}
```

前端用 `FormData` POST 到 `host`（`file` 必须是最后一个表单域），字段如下：

| 表单域 | 值 |
|--------|----|
| `success_action_status` | `200` |
| `policy` | 响应 `policy` |
| `x-oss-signature` | 响应 `signature` |
| `x-oss-signature-version` | `OSS4-HMAC-SHA256` |
| `x-oss-credential` | 响应 `x_oss_credential` |
| `x-oss-date` | 响应 `x_oss_date` |
| `key` | 响应 `key`（不要用 `dir + 原文件名`） |
| `x-oss-security-token` | 响应 `security_token` |
| `callback` | 响应 `callback` |
| `Content-Type` | 与签发时的 `contentType` 一致 |
| `x:scene` | `avatar` 或 `product` |
| `x:userId` | 当前用户 ID 字符串 |
| `file` | 文件本体，必须放最后 |

Bucket 需配置 CORS（Methods 含 POST）。`ALIYUN_OSS_CALLBACK_URL` 仅在公网可达时才写入签发结果；本地开发请留空，直传成功后用返回的 `accessUrl` 调用 `PATCH /api/users/me` 或 `PUT /api/admin/products/{id}/images` 入库。若填了 `localhost` 回调地址，服务端会自动忽略，避免 OSS 判定上传失败。

### 14.2 上传回调

```http
POST /api/oss/callback
```

由 OSS 服务器调用，不携带用户 JWT。服务端校验 `authorization` 与 `x-oss-pub-key-url` 的 RSA 签名；公钥 URL 必须属于 `gosspublic.alicdn.com`。验签失败返回 HTTP 400，OSS 会判定本次上传失败。

回调 body（`application/x-www-form-urlencoded`）包含 `bucket`、`object`、`size`、`mimeType`、`scene`、`userId`。`scene=avatar` 时写入对应用户的 `avatarUrl`；`scene=product` 只返回 `accessUrl`，需再调用 12.3 写入商品。

成功时 HTTP 200，`Content-Type: application/json`，OSS 会把该 JSON 转发给浏览器。`data` 示例：

```json
{
  "object": "avatars/10001/ab12cd.png",
  "accessUrl": "https://cdn.example.com/avatars/10001/ab12cd.png",
  "scene": "avatar"
}
```

## 14. 数据一致性和实现顺序

### 14.1 库存扣减

库存直接使用 `product.stock`，不创建独立库存表。创建订单时建议使用带条件的更新：

```sql
UPDATE product
SET stock = stock - #{quantity}
WHERE id = #{productId}
  AND status = 'on_sale'
  AND stock >= #{quantity}
  AND deleted_at = 0;
```

受影响行数为 `0` 时返回库存不足，并回滚订单事务。

### 14.2 订单地址

创建订单时将 `user_address` 的完整内容复制到 `orders` 的收货字段。之后用户修改或删除地址，不影响已经创建的订单。

### 14.3 金额计算

- 商品小计：订单明细 `unitPrice * quantity` 之和
- 运费：按照商品当前固定运费规则计算
- 订单总额：商品小计加运费
- 所有金额由服务端计算，客户端金额只用于展示

### 14.4 推荐实现顺序

1. 统一响应、异常处理和 Token 鉴权
2. 分类和商品列表、详情、评价查询
3. 用户资料和地址
4. 购物车及游客购物车合并
5. 收藏、浏览足迹和搜索历史
6. 结算预览、订单创建和库存扣减
7. 支付、超时取消和确认收货
8. 提交评价和订单完成
9. OSS 签名直传与头像/商品图落库

## 15. 本地初始化

创建新数据库和表：

```bash
mysql -uroot -p < sql/create_core_business_tables.sql
```

插入测试数据：

```bash
mysql -uroot -p < sql/insert_demo_data.sql
```

应用配置使用数据库环境变量：

```text
MYSQL_DATABASE=orange_market_simple
```

测试数据脚本中的示例用户手机号：

```text
13800138001
13800138002
13800138003
```
