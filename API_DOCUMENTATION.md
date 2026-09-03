# 橙子商城后端接口文档

## 1. 文档说明

本文档根据当前前端项目的路由、TypeScript 类型、Zustand 状态、Axios 封装和 MSW Mock 接口整理，用于后端 Controller、Service、DTO 和接口联调开发。

前端项目当前使用的 API 基地址为 `/api`，参考文件：

- `OrangeMarketFrontend/src/lib/api.ts`
- `OrangeMarketFrontend/src/mocks/handlers.ts`
- `OrangeMarketFrontend/src/types.ts`
- `OrangeMarketFrontend/src/store/useStore.ts`

当前业务范围：

- 仅 C 端用户，不提供后台管理 API
- 手机号 + 短信验证码登录，首次登录自动注册
- 开发阶段短信验证码固定为 `123456`
- 当前使用模拟支付，不接入真实支付渠道
- 商品无 SKU 规格，一个商品对应一个库存单元
- 不提供优惠券和发票功能
- 商品、库存、秒杀活动等数据暂时通过 SQL 或数据库工具维护

## 2. 全局约定

### 2.1 基础信息

| 项目 | 约定 |
| --- | --- |
| Base URL | `/api` |
| 数据格式 | `application/json` |
| 字符集 | UTF-8 |
| 时间格式 | ISO 8601，例如 `2026-08-31T12:30:00.000Z` |
| ID 格式 | API 统一返回字符串，数据库内部可以使用 BIGINT |
| 金额格式 | API 返回数字且保留两位小数，数据库使用 DECIMAL |
| 分页页码 | 从 `1` 开始 |
| 默认 pageSize | `12` |
| 最大 pageSize | `50` |

### 2.2 请求头

登录后请求需要携带：

```http
Authorization: Bearer {token}
Content-Type: application/json
```

建议订单创建、支付和评价请求额外携带：

```http
Idempotency-Key: {client-generated-key}
X-Request-Id: {request-id}
```

### 2.3 统一成功响应

当前前端 Mock 使用以下格式，后端应保持一致：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1788179400000
}
```

### 2.4 统一失败响应

```json
{
  "code": 40001,
  "message": "短信验证码错误",
  "data": null,
  "timestamp": 1788179400000
}
```

HTTP 状态码和业务错误码同时使用：

| HTTP 状态 | 业务错误码 | 说明 |
| --- | --- | --- |
| `400` | `40000` | 请求参数错误 |
| `400` | `40001` | 验证码错误或已过期 |
| `400` | `40002` | 手机号格式错误 |
| `400` | `40003` | 短信发送过于频繁 |
| `401` | `40100` | 未登录或 Token 无效 |
| `403` | `40300` | 无权访问该资源 |
| `404` | `40400` | 资源不存在 |
| `409` | `40900` | 数据冲突、库存不足或重复操作 |
| `422` | `42200` | 当前业务状态不允许此操作 |
| `429` | `42900` | 请求频率过高 |
| `500` | `50000` | 服务端异常 |

### 2.5 分页响应

商品、评价、订单、足迹等列表统一返回：

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 12,
  "hasMore": false
}
```

当前前端已经使用的商品列表格式至少需要包含 `list`、`total` 和 `hasMore`。

## 3. 核心数据结构

### 3.1 用户 `User`

```json
{
  "id": "10001",
  "phone": "13800138000",
  "nickname": "橙子同学",
  "avatar": "https://example.com/avatar.png",
  "gender": "保密",
  "birthday": "1998-08-18"
}
```

后端数据库字段 `avatar_url` 对外转换为 `avatar`。

### 3.2 分类 `Category`

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

`seckill` 可以作为 `isVirtual=true` 的虚拟分类，实际商品筛选依据是有效秒杀活动。

### 3.3 商品 `Product`

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
  "rating": 4.9,
  "reviewCount": 86,
  "description": "商品详细描述",
  "shippingFee": 8.00,
  "isSeckill": true,
  "seckillPrice": 195.98,
  "seckillStock": 100,
  "seckillSold": 80,
  "startTime": "2026-08-31T10:00:00.000Z",
  "endTime": "2026-08-31T22:00:00.000Z",
  "purchaseLimit": 1,
  "tags": ["热卖", "秒杀"]
}
```

字段说明：

- `price` 为当前普通售价
- 秒杀进行中时，`seckillPrice` 为当前有效售价
- `stock` 为普通商品可售库存
- `seckillStock` 和 `seckillSold` 为当前秒杀活动展示数据
- `rating`、`reviewCount` 和 `sales` 为商品展示用统计数据
- `shippingFee` 为该商品固定运费

### 3.4 评价 `Review`

```json
{
  "id": "50001",
  "productId": "20001",
  "userName": "小橙子",
  "avatar": "https://example.com/avatar.png",
  "content": "质感比预期更好，物流也很快。",
  "quality": 5,
  "service": 5,
  "logistics": 5,
  "images": ["https://example.com/review-image.jpg"],
  "anonymous": false,
  "createdAt": "2026-08-30T12:30:00.000Z"
}
```

匿名评价仍然返回评价数据，但 `userName` 应返回“匿名用户”，不能暴露真实昵称。

### 3.5 地址 `Address`

```json
{
  "id": "30001",
  "receiver": "橙子同学",
  "phone": "138****8000",
  "province": "上海市",
  "city": "上海市",
  "district": "浦东新区",
  "detail": "世纪大道 100 号橙子大厦 8 楼",
  "isDefault": true
}
```

手机号写入数据库时应保存完整值，响应给前端时脱敏。

### 3.6 购物车商品 `CartItem`

```json
{
  "id": "40001",
  "productId": "20001",
  "quantity": 2,
  "selected": true,
  "product": {},
  "effectivePrice": 195.98,
  "shippingFee": 8.00
}
```

`product`、`effectivePrice` 和 `shippingFee` 是购物车接口返回的展示辅助字段，数据库不必重复存储商品名称和图片。

### 3.7 订单 `Order`

```json
{
  "id": "60001",
  "orderNo": "OM202608310001",
  "status": "pending_payment",
  "items": [],
  "address": {},
  "subtotal": 391.96,
  "shippingFee": 8.00,
  "total": 399.96,
  "buyerRemark": "请尽快发货",
  "createdAt": "2026-08-31T12:30:00.000Z",
  "expiresAt": "2026-08-31T13:00:00.000Z"
}
```

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

## 4. 认证接口

### 4.1 获取图形验证码

```http
POST /api/auth/captcha
```

是否需要登录：否。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "captchaId": "captcha-abc123",
    "imageUrl": "data:image/svg+xml;base64,...",
    "expiresIn": 300
  },
  "timestamp": 1788179400000
}
```

开发阶段也可以继续由前端生成图形验证码，但正式接入后建议由服务端生成并保存答案摘要。

### 4.2 发送短信验证码

```http
POST /api/auth/sms/send
```

请求体：

```json
{
  "phone": "13800138000",
  "purpose": "login",
  "captchaId": "captcha-abc123",
  "captchaCode": "7K3M"
}
```

响应：

```json
{
  "code": 0,
  "message": "短信验证码已发送",
  "data": {
    "cooldown": 60,
    "expiresIn": 300
  },
  "timestamp": 1788179400000
}
```

开发环境约定：短信验证码固定为 `123456`，但仍然需要校验手机号、图形验证码和 60 秒发送频率。

Redis Key：

```text
orange:auth:sms:code:login:{phone}   TTL 300 秒
orange:auth:sms:limit:login:{phone}  TTL 60 秒
```

### 4.3 手机号登录或注册

```http
POST /api/auth/login
```

请求体：

```json
{
  "phone": "13800138000",
  "smsCode": "123456",
  "captchaId": "captcha-abc123",
  "captchaCode": "7K3M"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "token-value",
    "expiresAt": "2026-09-07T12:30:00.000Z",
    "isNewUser": true,
    "user": {
      "id": "10001",
      "phone": "13800138000",
      "nickname": "橙子用户8000",
      "avatar": "https://example.com/avatar.png",
      "gender": "保密",
      "birthday": ""
    }
  },
  "timestamp": 1788179400000
}
```

手机号不存在时，在同一个事务中创建用户并登录。手机号已存在时直接登录。

### 4.4 获取当前用户

```http
GET /api/auth/me
```

是否需要登录：是。

响应 `data` 为 `User` 对象。

### 4.5 退出登录

```http
POST /api/auth/logout
```

是否需要登录：是。

服务端删除或废弃当前 Redis Token。

## 5. 首页、分类与商品接口

### 5.1 获取分类

```http
GET /api/categories
```

是否需要登录：否。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "10001",
      "name": "数码",
      "eyebrow": "SMART LIFE",
      "color": "#e7f5ff",
      "icon": "Laptop",
      "isVirtual": false
    }
  ],
  "timestamp": 1788179400000
}
```

### 5.2 商品列表

```http
GET /api/products
```

是否需要登录：否。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | `integer` | 否 | `1` | 页码 |
| `pageSize` | `integer` | 否 | `12` | 每页数量，最大 50 |
| `keyword` | `string` | 否 | 空 | 搜索商品名称、副标题和描述 |
| `categoryId` | `string` | 否 | 空 | 普通分类 ID |
| `seckillOnly` | `boolean` | 否 | `false` | 是否只查询秒杀商品 |
| `sort` | `string` | 否 | `default` | `default`、`price_asc`、`sales_desc` |

示例：

```http
GET /api/products?page=1&pageSize=12&keyword=跑鞋&sort=sales_desc
```

响应 `data`：

```json
{
  "list": [],
  "total": 36,
  "page": 1,
  "pageSize": 12,
  "hasMore": true
}
```

注意：排序必须在数据库分页前执行，否则无限滚动时只会对当前已经加载的页面排序。

### 5.3 商品详情

```http
GET /api/products/{productId}
```

是否需要登录：否。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "product": {},
    "reviews": [],
    "reviewSummary": {
      "average": 4.9,
      "quality": 4.9,
      "service": 4.8,
      "logistics": 4.9,
      "reviewCount": 86,
      "goodRate": 0.98
    }
  },
  "timestamp": 1788179400000
}
```

`reviews` 可以返回商品详情页需要展示的少量预览评价，全部评价通过下一接口分页查询。

商品不存在时返回 HTTP `404` 和业务错误码 `40400`。

### 5.4 商品全部评价

```http
GET /api/products/{productId}/reviews
```

是否需要登录：否。

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `filter` | `string` | `all` | `all`、`good`、`medium`、`bad`、`media` |
| `page` | `integer` | `1` | 页码 |
| `pageSize` | `integer` | `10` | 每页数量 |
| `sort` | `string` | `latest` | `latest`、`helpful`，当前只需实现 latest |

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "total": 86,
    "page": 1,
    "pageSize": 10,
    "hasMore": true,
    "summary": {
      "average": 4.9,
      "quality": 4.9,
      "service": 4.8,
      "logistics": 4.9,
      "goodRate": 0.98,
      "allCount": 86,
      "goodCount": 82,
      "mediumCount": 3,
      "badCount": 1,
      "mediaCount": 24
    }
  },
  "timestamp": 1788179400000
}
```

筛选规则：

- `good`：质量、服务、物流均大于等于 4
- `medium`：任一维度等于 3
- `bad`：任一维度小于等于 2
- `media`：存在 `review_media` 记录

## 6. 购物车接口

游客购物车可以暂时保存在浏览器本地。登录后，前端调用合并接口同步到服务端。

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
    "subtotal": 391.96,
    "shippingFee": 8.00,
    "total": 399.96
  },
  "timestamp": 1788179400000
}
```

### 6.2 添加购物车商品

```http
POST /api/cart/items
```

请求体：

```json
{
  "productId": "20001",
  "quantity": 1,
  "seckillActivityId": "70001"
}
```

`seckillActivityId` 可为空。当前无 SKU，不需要 `skuId`。

### 6.3 修改购物车商品数量或选中状态

```http
PATCH /api/cart/items/{cartItemId}
```

请求体：

```json
{
  "quantity": 2,
  "selected": true
}
```

两个字段均可单独传递。数量不能小于 1，且不能超过当前可售库存。

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

服务器应按商品 ID 合并数量，并重新校验库存。

## 7. 用户资料、地址、收藏和足迹接口

### 7.1 获取用户资料

```http
GET /api/users/me
```

是否需要登录：是。

### 7.2 修改用户资料

```http
PATCH /api/users/me
```

请求体：

```json
{
  "nickname": "橙子同学",
  "gender": "保密",
  "birthday": "1998-08-18"
}
```

当前头像由系统生成，头像上传接口可以后置。

### 7.3 获取地址列表

```http
GET /api/users/me/addresses
```

是否需要登录：是。

### 7.4 新增地址

```http
POST /api/users/me/addresses
```

请求体：

```json
{
  "receiver": "橙子同学",
  "phone": "13800138000",
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

### 7.5 修改地址

```http
PUT /api/users/me/addresses/{addressId}
```

请求体与新增地址相同。

### 7.6 删除地址

```http
DELETE /api/users/me/addresses/{addressId}
```

默认地址删除后，服务端可以将用户最近创建的其他地址设为默认地址。

### 7.7 设置默认地址

```http
PUT /api/users/me/addresses/{addressId}/default
```

必须在事务中取消旧默认地址并设置新默认地址。

### 7.8 获取收藏夹

```http
GET /api/users/me/favorites?page=1&pageSize=20
```

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

重复收藏应返回成功或 `40900`，建议使用幂等成功。

### 7.10 取消收藏

```http
DELETE /api/users/me/favorites/{productId}
```

### 7.11 获取浏览足迹

```http
GET /api/users/me/browse-history?page=1&pageSize=20
```

响应中的每条记录建议包含：

```json
{
  "id": "80001",
  "productId": "20001",
  "viewedAt": "2026-08-31T12:30:00.000Z",
  "priceAtView": 239.00,
  "hasPriceDrop": true,
  "product": {}
}
```

前端按照 `viewedAt` 自行分组为“今天、昨天、近 7 天、更早”。

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

浏览价格由服务端根据当前有效售价计算，不能信任客户端传入的价格。

同一用户重复浏览同一商品时更新原记录，并限制最近 100 条。

### 7.13 清空浏览足迹

```http
DELETE /api/users/me/browse-history
```

### 7.14 批量删除浏览足迹

```http
POST /api/users/me/browse-history/batch-delete
```

请求体：

```json
{
  "ids": ["80001", "80002"]
}
```

## 8. 订单与结算接口

### 8.1 结算预览

```http
POST /api/orders/preview
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
    "subtotal": 391.96,
    "shippingFee": 8.00,
    "total": 399.96,
    "paymentExpireMinutes": 30
  },
  "timestamp": 1788179400000
}
```

结算预览必须重新校验商品状态、库存、秒杀活动和有效价格。

### 8.2 创建订单

```http
POST /api/orders
```

请求头：

```http
Idempotency-Key: order-create-unique-key
```

请求体：

```json
{
  "cartItemIds": ["40001", "40002"],
  "addressId": "30001",
  "buyerRemark": "请尽快发货"
}
```

响应：

```json
{
  "code": 0,
  "message": "订单创建成功",
  "data": {
    "orderId": "60001",
    "orderNo": "OM202608310001",
    "status": "pending_payment",
    "total": 399.96,
    "paymentExpireAt": "2026-08-31T13:00:00.000Z"
  },
  "timestamp": 1788179400000
}
```

服务端事务必须同时完成：订单、地址快照、订单商品快照、库存锁定、订单状态历史和购物车清理。

### 8.3 获取订单列表

```http
GET /api/orders
```

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `status` | `string` | 空 | 订单状态，不传表示全部 |
| `page` | `integer` | `1` | 页码 |
| `pageSize` | `integer` | `10` | 每页数量 |

前端订单状态筛选值：

```text
pending_payment
pending_shipment
pending_receipt
pending_review
refunding
```

### 8.4 获取订单详情

```http
GET /api/orders/{orderId}
```

是否需要登录：是，只能查看自己的订单。

响应 `data` 为完整 `Order`，并可以额外包含：

```json
{
  "statusTimeline": [],
  "shipment": null,
  "availableActions": ["pay", "refund", "receive", "review"]
}
```

### 8.5 取消订单

```http
POST /api/orders/{orderId}/cancel
```

请求体：

```json
{
  "reason": "不想要了"
}
```

用户只能取消待付款订单。超时取消由系统任务调用相同的业务服务完成。

### 8.6 确认收货

```http
POST /api/orders/{orderId}/receive
```

状态要求：`pending_receipt`。

成功后订单变为 `pending_review`。

### 8.7 获取售后订单列表

```http
GET /api/after-sales
```

查询参数：

```text
status=pending|approved|rejected|refunded|cancelled
page=1
pageSize=10
```

## 9. 支付接口

### 9.1 创建支付交易

```http
POST /api/orders/{orderId}/payments
```

请求体：

```json
{
  "paymentMethod": "wechat"
}
```

支付方式：

```text
wechat
alipay
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "paymentId": "90001",
    "paymentNo": "PAY202608310001",
    "paymentMethod": "wechat",
    "amount": 399.96,
    "status": "pending",
    "expiresAt": "2026-08-31T13:00:00.000Z"
  },
  "timestamp": 1788179400000
}
```

### 9.2 开发环境模拟支付成功

```http
POST /api/payments/{paymentId}/mock-success
```

此接口仅允许开发环境使用。

成功后必须：

1. 支付记录改为 `success`
2. 订单从 `pending_payment` 改为 `pending_shipment`
3. 锁定库存转为已售库存
4. 增加商品销量和秒杀销量
5. 写入订单状态历史

### 9.3 取消支付

```http
POST /api/payments/{paymentId}/cancel
```

取消支付不会立即取消订单，订单仍然可以在 30 分钟内重新支付。

### 9.4 查询支付记录

```http
GET /api/orders/{orderId}/payments
```

只能查看当前用户自己的订单支付记录。

## 10. 售后和退款接口

### 10.1 创建售后申请

```http
POST /api/orders/{orderId}/after-sales
```

请求体：

```json
{
  "type": "refund_only",
  "reason": "商品不合适",
  "description": "希望申请退款",
  "items": [
    {
      "orderItemId": "61001",
      "quantity": 1
    }
  ]
}
```

售后类型：

```text
refund_only    仅退款
return_refund  退货退款，当前可暂不实现退货物流
```

订单进入 `refunding` 状态后，个人中心的退款售后列表可以查询到该记录。

### 10.2 获取售后详情

```http
GET /api/after-sales/{afterSaleId}
```

### 10.3 取消售后申请

```http
POST /api/after-sales/{afterSaleId}/cancel
```

只允许取消尚未处理的售后申请。

### 10.4 开发环境模拟退款成功

```http
POST /api/after-sales/{afterSaleId}/mock-refund
```

此接口仅允许开发环境使用。成功后创建退款流水并将订单改为 `refunded`。

## 11. 评价接口

### 11.1 获取待评价订单商品

```http
GET /api/users/me/reviews/pending
```

用于展示当前用户还没有评价的订单商品。

### 11.2 提交订单评价

```http
POST /api/orders/{orderId}/reviews
```

请求体：

```json
{
  "reviews": [
    {
      "orderItemId": "61001",
      "content": "质量不错，物流也很快。",
      "quality": 5,
      "service": 5,
      "logistics": 5,
      "anonymous": false,
      "media": []
    }
  ]
}
```

校验规则：

- 订单必须属于当前用户
- 订单必须处于 `pending_review`
- `orderItemId` 必须属于该订单
- 三个评分都必须是 1 到 5 的整数
- 评价内容不能为空
- 同一个订单商品只能评价一次
- 评价成功后订单改为 `completed`

当前前端评价图片和视频只做本地预览，不上传服务器，因此开发阶段 `media` 可以为空数组。后续接入上传后，`media` 使用媒体地址或媒体 ID。

### 11.3 上传评价媒体，后置

```http
POST /api/reviews/media
Content-Type: multipart/form-data
```

该接口后置实现，文件保存到对象存储，MySQL 只保存 URL 和 Object Key。

## 12. 搜索历史接口

### 12.1 获取搜索历史

```http
GET /api/users/me/search-history
```

响应按 `searchedAt` 倒序返回最近 10 条。

### 12.2 写入搜索关键词

```http
POST /api/users/me/search-history
```

请求体：

```json
{
  "keyword": "跑鞋"
}
```

相同关键词再次搜索时更新时间并移动到第一位。

### 12.3 删除单条搜索历史

```http
DELETE /api/users/me/search-history/{historyId}
```

### 12.4 清空搜索历史

```http
DELETE /api/users/me/search-history
```

游客搜索历史继续由前端本地保存。

## 13. 客服接口和 WebSocket

客服页面支持游客访问，因此会话中的 `userId` 可以为空，使用 `visitorToken` 识别游客。

### 13.1 获取当前客服会话

```http
GET /api/service/sessions/current
```

登录用户使用 Token 识别，游客使用请求头：

```http
X-Visitor-Token: visitor-token
```

没有会话时可以自动创建一个新会话。

### 13.2 创建客服会话

```http
POST /api/service/sessions
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "110001",
    "status": "active",
    "visitorToken": "visitor-token"
  },
  "timestamp": 1788179400000
}
```

### 13.3 获取历史消息

```http
GET /api/service/sessions/{sessionId}/messages?cursor=0&limit=30
```

响应消息格式：

```json
{
  "id": "120001",
  "sender": "service",
  "content": "你好呀，我是橙子小助手，有什么可以帮你？",
  "createdAt": "2026-08-31T12:30:00.000Z",
  "productId": null,
  "orderId": null,
  "messageType": "text"
}
```

`sender` 对齐前端类型，取值为 `user` 或 `service`。

### 13.4 HTTP 发送消息

```http
POST /api/service/sessions/{sessionId}/messages
```

请求体：

```json
{
  "messageType": "text",
  "content": "商品什么时候发货？",
  "productId": null,
  "orderId": null,
  "payload": null
}
```

消息类型：

```text
text
product_card
order_card
faq
```

商品卡片和订单卡片建议在 `payload` 中保存展示快照。

### 13.5 WebSocket 连接

```text
ws://{host}/ws/service?sessionId={sessionId}&visitorToken={visitorToken}
```

客户端发送：

```json
{
  "type": "message",
  "data": {
    "messageType": "text",
    "content": "你好"
  }
}
```

服务端推送：

```json
{
  "type": "message",
  "data": {
    "id": "120002",
    "sender": "service",
    "content": "收到啦，我已经记下你的问题。",
    "createdAt": "2026-08-31T12:30:01.000Z",
    "messageType": "text"
  }
}
```

连接建立、断开和心跳事件：

```json
{
  "type": "connected",
  "data": {
    "sessionId": "110001"
  }
}
```

在线状态可以使用 Redis 保存，不写入 MySQL。

### 13.6 获取 FAQ

```http
GET /api/service/faqs
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": [
    {
      "id": "130001",
      "question": "商品什么时候发货？",
      "answer": "现货订单会在 24 小时内安排发出，请耐心等待物流更新哦。"
    }
  ],
  "timestamp": 1788179400000
}
```

## 14. 状态流转规则

### 14.1 订单状态

```text
创建订单       -> pending_payment
支付成功       -> pending_shipment
系统发货       -> pending_receipt
确认收货       -> pending_review
完成评价       -> completed
支付超时       -> cancelled
申请售后       -> refunding
退款成功       -> refunded
```

以下状态变化必须写入 `order_status_history`：

- 原状态
- 新状态
- 操作来源：`user` 或 `system`
- 操作者 ID
- 变更原因
- 变更时间

### 14.2 支付超时自动取消

后端定时任务每分钟扫描：

```text
status = pending_payment
payment_expire_at < 当前时间
```

并在事务中取消订单、释放普通库存或秒杀库存、更新库存锁定记录。

前端倒计时只用于展示，不能作为订单自动取消的实际依据。

### 14.3 库存状态

普通商品和秒杀商品都需要支持：

```text
available_stock  可售库存
locked_stock     订单锁定库存
sold_stock       已售库存
```

创建订单时锁定库存，支付成功时转为已售，取消订单时释放库存。

## 15. 接口权限总览

| 接口模块 | 游客 | 登录用户 |
| --- | --- | --- |
| 分类、商品、搜索商品 | 支持 | 支持 |
| 商品详情和评价 | 支持 | 支持 |
| 客服页面 | 支持 | 支持 |
| 购物车 | 本地存储 | 服务端购物车 |
| 创建订单、结算 | 不支持 | 支持 |
| 支付 | 不支持 | 支持 |
| 订单、地址 | 不支持 | 支持 |
| 收藏 | 本地临时状态 | 服务端保存 |
| 浏览足迹 | 本地临时状态 | 服务端保存 |
| 提交评价 | 不支持 | 支持 |
| 售后退款 | 不支持 | 支持 |
| 搜索历史 | 本地临时状态 | 服务端保存 |

## 16. 前端接入注意事项

### 16.1 当前 Mock 接口

当前前端已经定义并使用：

```http
GET /api/categories
GET /api/products
GET /api/products/{productId}
```

后端优先实现这三个接口即可完成商品列表和商品详情的首次联调。

### 16.2 搜索参数

前端搜索框跳转地址为：

```text
/search?q=跑鞋
```

前端请求后端时需要把 URL 参数 `q` 转换为接口参数 `keyword`。

### 16.3 评价全部列表

商品详情页的“查看全部评价”跳转到：

```text
/product/{productId}/reviews
```

因此必须实现：

```http
GET /api/products/{productId}/reviews
```

不能只在商品详情接口中返回固定的少量评价。

### 16.4 金额计算

商品售价、秒杀价、运费和订单总额都由服务端重新计算，前端传入的金额只能作为展示参考。

当前前端规则是每个商品固定收取一次运费，同一商品增加数量不会重复收取该商品运费。订单创建时将最终金额写入订单和订单商品快照。

### 16.5 商品排序和无限滚动

商品列表接口需要在数据库分页前完成排序。前端的无限滚动依赖：

```json
{
  "list": [],
  "total": 0,
  "hasMore": false
}
```

### 16.6 脱敏规则

- 用户手机号响应时脱敏
- 收货手机号响应时脱敏
- 匿名评价返回“匿名用户”
- 不在日志中打印短信验证码、Token 和支付敏感信息

## 17. 推荐后端实现顺序

1. 统一响应体、异常处理和 Token 鉴权
2. 分类、商品列表、商品详情和商品评价列表
3. 手机验证码登录和用户资料
4. 地址、购物车、收藏和足迹
5. 结算预览、订单创建和订单查询
6. 模拟支付、支付超时取消和库存释放
7. 确认收货、评价提交和订单完成
8. 售后申请和模拟退款
9. FAQ、客服 HTTP 接口和 WebSocket
10. Redis 缓存、秒杀原子扣库存和异步足迹持久化

## 18. 暂不实现的接口

当前不需要实现：

- 后台管理员登录和商品管理接口
- 商家端接口
- 优惠券接口
- 发票接口
- SKU 规格接口
- 真实微信支付和支付宝支付接口
- 真实物流平台回调接口
- 评价图片和视频上传接口
- 价格提醒推送接口
