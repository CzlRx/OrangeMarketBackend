# 橙子商城前端对接开发文档（后端接口契约 v1.0）

> 本文档由后端源码 `OrangeMarketBackend`（Spring Boot 4.1.1 / Java 21 / MyBatis-Plus / Redis / Shiro+JWT）逐行核对生成。
> 所有字段名、大小写、枚举值、JSON 结构均已通过实际序列化验证（Jackson 3）。
> **前端 Agent 仅凭本文档即可完整、无误地编写对接前端代码，无需再读后端源码。**

---

## 目录

1. [文档目的与使用方式](#1-文档目的与使用方式)
2. [环境与启动信息](#2-环境与启动信息)
3. [全局通信约定](#3-全局通信约定)
4. [认证与登录流程](#4-认证与登录流程)
5. [全局错误码表](#5-全局错误码表)
6. [TypeScript 类型定义（可直接使用）](#6-typescript-类型定义可直接使用)
7. [接口明细](#7-接口明细)
8. [订单状态机与前端交互规则](#8-订单状态机与前端交互规则)
9. [购物车与游客购物车同步](#9-购物车与游客购物车同步)
10. [页面 → 接口 映射表](#10-页面--接口-映射表)
11. [前端实现建议（API 封装示例）](#11-前端实现建议api-封装示例)
12. [后端已知问题与注意事项](#12-后端已知问题与注意事项)

---

## 1. 文档目的与使用方式

- 本文档是前端与后端对接的**唯一权威依据**，内容与后端当前代码完全一致。
- 约定：所有接口前缀为 `/api`；登录后请求必须携带 `Authorization: Bearer {Token}`。
- 参考前端项目：`OrangeMarketFrontend`（React + Vite + TypeScript，目前使用 Mock 数据）。对接时把 Mock 数据层替换为本文档定义的 API 层即可，页面结构可沿用。
- 本文档中所有「请求示例」是前端**发送**的 JSON；所有「响应示例」是后端原样返回的 JSON（字段顺序与真实一致，字段完整性按 @JsonInclude 规则）。

---

## 2. 环境与启动信息

| 项目 | 值 |
| --- | --- |
| 后端地址 | `http://localhost:8080`（server.port=8080） |
| API 前缀 | `/api`（Base URL = `http://localhost:8080/api`） |
| 数据格式 | `application/json`，UTF-8 |
| 依赖服务 | MySQL（库名 `orange_market_simple`）、Redis、RabbitMQ |
| 项目结构 | `OrangeMarketBackend`（当前工作区）、`OrangeMarketFrontend`（同级前端） |

前端本地开发建议配置 Vite 代理，把 `/api` 转发到 `http://localhost:8080`，避免跨域：

```ts
// vite.config.ts
export default defineConfig({
  server: {
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
})
```

### 演示数据与账号

- 登录：**任意** `1` 开头的 11 位手机号，首次登录自动注册（昵称自动生成 `橙子用户{手机号后4位}`）。
- 短信验证码：**固定为 `1234`**（后端当前为模拟发送，任何手机号都可用）。
- 图形验证码：由 `GET /api/auth/captcha` 返回图片 Base64。
- 预置演示手机号（来自初始化脚本）：`13800138001`、`13800138002`、`13800138003`。

---

## 3. 全局通信约定

### 3.1 统一响应包装 `Result`

所有业务接口（除 401 认证失败的特殊情况，见 3.3）返回以下结构：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1788868837766
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | number | 业务码；`0` = 成功，非 0 = 失败（见错误码表） |
| `message` | string | 提示文案；失败时可直接展示给用户 |
| `data` | object/null | 业务数据；**无内容的成功接口返回 `{}`（空对象），不是 null；失败时是 `null`** |
| `timestamp` | number | 毫秒时间戳 |

### 3.2 鉴权方式

- 登录后，前端必须保存登录响应中的 **`Token`**（注意：**大写的 `T`**，见第 6 节 `LoginData`）。
- 每次请求携带请求头：`Authorization: Bearer {Token}`（`Bearer` 后面一个空格）。
- Token 有效期与 Redis 登录会话均为 **24 小时**；登出、被管理员封禁后立即失效。
- 公开接口（无需登录）：`/api/auth/captcha`、`/api/auth/sms/send`、`/api/auth/login`、`/api/categories`、`/api/products`、`/api/products/**`。其余 `/api/**` 一律需要登录。

### 3.3 认证失败的响应（特殊结构，务必注意）

当未带 Token / Token 无效 / 会话已过期 / 用户被封禁时，过滤器直接返回 **HTTP 401**，响应体**并非标准 Result**，而是：

```json
{ "code": 401, "message": "会话已过期，请重新登录" }
```

- 可能出现的 message：`缺少认证 token`、`token 无效或已过期`、`token 缺少用户信息`、`会话已过期，请重新登录`。
- **没有 `data` 和 `timestamp` 字段**。前端拦截器应识别：HTTP 401 或 `code === 401` 时清除本地登录态并跳转登录页。

### 3.4 其他失败响应（业务异常）

由全局异常处理器返回，HTTP 状态码与 `code` 前三位对应（如 `code=40000` 时 HTTP 400）。结构为标准 `Result`，`data: null`：

```json
{ "code": 40000, "message": "请求参数错误", "data": null, "timestamp": 1788868837766 }
```

### 3.5 数据类型约定

| 类型 | 约定 |
| --- | --- |
| ID | 请求与响应中的 ID（商品、订单、地址、购物车项等）**均为字符串**（后端 Long 转 String）。**唯一例外**：`GET /api/auth/me` 返回的实体 ID 是数字 |
| 金额 | JSON number，后端保留两位小数（如 `19.90`、`0.00`），前端用 number 处理即可 |
| 时间 | 绝大多数为 ISO-8601 无时区字符串 `yyyy-MM-ddTHH:mm:ss`（如 `2026-09-08T10:00:00`），是服务端本地时间（部署时区 Asia/Shanghai），**不带时区后缀**；前端直接 `new Date(str)` 即可按本地时间解析 |
| 日期 | `birthday` 为 `yyyy-MM-dd`（如 `1998-08-18`） |
| 登录过期时间 | `expiresAt` 是带 `Z` 的 UTC 字符串（如 `2026-09-09T00:00:00Z`），由服务端 `Instant` 转字符串 |
| 枚举 | 一律输出为字符串（如订单状态 `"pending_payment"`、用户状态 `"active"`） |
| 可空字段 | 标注了 `@JsonInclude(NON_NULL)` 的响应对象，**值为 null 的字段会被省略不输出**；前端类型用「可选字段」处理 |

### 3.6 分页结构

列表接口统一返回：

```json
{ "list": [], "total": 0, "page": 1, "pageSize": 12, "hasMore": false }
```

- 分页参数：`page`（从 1 开始）、`pageSize`（各接口默认值与上限见接口明细，**最大均为 50**）。
- 非法分页（page<1 或 pageSize<1 或超上限）→ `40000 分页参数错误`。

---

## 4. 认证与登录流程

登录三步流程（全部公开接口）：

```
1. GET  /api/auth/captcha      → 得到图形验证码图片 + captchaKey
2. POST /api/auth/sms/send     → 校验图形验证码，发送短信（实际固定 1234）
3. POST /api/auth/login        → 手机号 + 短信验证码，得到 Token
```

详细接口见第 7 节 A 模块。登录成功后：

- 保存 `Token`、`expiresAt`、`newUser` 到本地（建议 localStorage）。
- 携带 `Authorization: Bearer {Token}` 访问受保护接口。
- **游客购物车合并**：登录后调用 `POST /api/cart/merge` 把本地购物车同步到服务端（见第 9 节）。

> ⚠️ 已知后端问题（影响联调，前端仍按正常逻辑实现）：
> 后端 `AuthService.sendSms` 的图形验证码判断逻辑是**反转**的——提交**正确**的图形验证码会返回 `40000 图形验证码错误`。前端照常提交即可，该问题需后端修复（把 `AuthService.sendSms` 中 `s.equals(smsRequest.getCaptchaCode())` 改为 `!s.equals(...)`）后才能正常联调。也可在联调前先让后端修复此 bug。

---

## 5. 全局错误码表

| code | HTTP | message（默认） | 触发场景 |
| --- | --- | --- | --- |
| 0 | 200 | success | 成功 |
| 40000 | 400 | 请求参数错误 | 参数缺失/格式错误/校验失败 |
| 40001 | 400 | 验证码错误或已过期 | 短信验证码错误或不存在 |
| 40002 | 400 | 手机号格式错误 | 手机号不匹配 `1\d{10}` |
| 40003 | 400 | 短信发送过于频繁 | 60 秒内重复发短信 |
| 40100 | 401 | 未登录或 Token 无效 | 业务层检测到会话失效（如 `/api/auth/me`） |
| 40300 | 403 | 无权访问该资源 | 角色不足（如普通用户访问管理端）或账号被封禁 |
| 40400 | 404 | 资源不存在 | 商品/订单/地址/历史记录不存在 |
| 40900 | 409 | 数据冲突、库存不足或重复操作 | 库存不足、重复评价等 |
| 42200 | 422 | 当前业务状态不允许此操作 | 订单状态不匹配（如未付款订单确认收货） |
| 42900 | 429 | 请求频率过高 | 短信发送过于频繁 |
| 50000 | 500 | 服务端异常 | 未知异常 |

前端建议逻辑：`code === 0` 视为成功；`HTTP 401` 或 `code === 401` 跳登录；其余失败展示 `message`。

---

## 6. TypeScript 类型定义（可直接使用）

以下类型与后端实际 JSON 输出逐字段对应，可直接复制到前端 `src/types.ts`（或作为 API 层类型）：

```ts
// ===== 通用 =====
export interface ApiResult<T> { code: number; message: string; data: T; timestamp: number }
export interface PageData<T> { list: T[]; total: number; page: number; pageSize: number; hasMore: boolean }

// ===== 认证 =====
export interface CaptchaData { image: string; captchaKey: string }                 // image 为 data:image/png;base64,...
export interface SmsInfo { cooldown: number; expiresIn: number }                  // 60 / 300
export interface LoginData {
  /** ⚠️ 后端字段就叫 Token（大写 T） */
  Token: string
  /** UTC 时间字符串，如 2026-09-09T00:00:00Z */
  expiresAt: string
  newUser: boolean
}
export type UserStatus = 'active' | 'disabled'
export type Gender = 0 | 1 | 2                                                    // 0 保密 1 男 2 女

// GET /api/auth/me 返回的原始用户实体（⚠️ id 是 number，字段无省略规则）
export interface AuthMeUser {
  id: number
  phone: string | null
  nickname: string | null
  avatarUrl: string | null
  gender: number | null
  birthday: string | null                     // yyyy-MM-dd
  status: UserStatus | null
  role: string | null                         // USER / ADMIN / admin
  lastLoginAt: string | null                  // yyyy-MM-ddTHH:mm:ss
  createdAt: string | null
  updatedAt: string | null
  deletedAt: number | null                    // 恒为 0
}

// GET/PATCH /api/users/me 的 profile（null 字段会被省略）
export interface UserProfile {
  id: string
  phone: string
  nickname: string
  avatarUrl?: string
  gender?: number
  birthday?: string
  status: UserStatus
  role: string
  lastLoginAt?: string
}

// ===== 分类 / 商品 =====
export interface Category { id: string; name: string; eyebrow: string; color: string; icon: string; isVirtual: boolean }

export interface Product {
  id: string
  name: string
  subtitle?: string
  categoryId?: string
  images: string[]
  videoUrl?: string
  price: number
  originalPrice: number
  stock: number
  sales: number
  rating?: number
  reviewCount: number
  description?: string
  shippingFee: number
  tags: string[]
}

export interface ProductDetail { product: Product; reviews: Review[]; reviewSummary: ReviewSummary }

// ===== 评价 =====
export interface Review {
  id: string
  productId: string
  userName: string                          // 匿名时固定为 "匿名用户"
  avatar?: string
  content: string
  rating: number                            // 1~5
  anonymous: boolean
  createdAt: string
}
export interface ReviewSummary {
  average: number
  reviewCount: number
  goodRate: number                          // 好评率 0~1
  allCount: number
  goodCount: number                         // rating >= 4
  mediumCount: number                       // rating == 3
  badCount: number                          // rating <= 2
}
export interface ProductReviewPage extends PageData<Review> { summary: ReviewSummary }

export interface ReviewItemRequest { orderItemId: string; productId: string; rating: number; content: string; anonymous?: boolean }
export interface SubmitReviewsRequest { reviews: ReviewItemRequest[] }
export interface PendingReviewItem {
  orderId: string
  orderNo: string
  orderItemId: string
  productId: string
  productName: string
  productImage: string
  unitPrice: number
  quantity: number
  lineAmount: number
}
export interface ReviewSubmissionResult {
  orderId: string
  orderNo: string
  status: OrderStatus
  reviewIds: string[]
  completedAt?: string
}

// ===== 购物车 =====
export interface CartItem {
  id: string
  productId: string
  quantity: number
  selected: boolean
  product?: Product
  effectivePrice?: number
  shippingFee?: number
}
export interface Cart { items: CartItem[]; selectedCount: number; subtotal: number; shippingFee: number; total: number }

// ===== 地址 =====
export interface Address {
  id: string
  receiver: string
  /** ⚠️ 后端做脱敏：138****0001 */
  phone: string
  provinceCode?: string
  province: string
  cityCode?: string
  city: string
  districtCode?: string
  district: string
  detail: string
  isDefault: boolean
}

// ===== 订单 =====
export type OrderStatus =
  | 'pending_payment' | 'pending_shipment' | 'pending_receipt'
  | 'pending_review' | 'completed' | 'cancelled' | 'refunding' | 'refunded'

export interface OrderItem { id: string; productId: string; productName: string; productImage: string; unitPrice: number; quantity: number; lineAmount: number }
export interface OrderAddress { receiver: string; phone: string; province: string; city: string; district: string; detail: string }
export interface Order {
  id: string
  orderNo: string
  status: OrderStatus
  items: OrderItem[]
  address: OrderAddress
  subtotal: number
  shippingFee: number
  total: number
  buyerRemark?: string
  paymentMethod?: string                    // "alipay" | "mock"
  trackingNo?: string
  createdAt: string
  paymentExpireAt?: string
  paidAt?: string
  shippedAt?: string
  receivedAt?: string
  completedAt?: string
  cancelledAt?: string
}
export interface OrderPreview {
  items: OrderItem[]
  address: OrderAddress
  subtotal: number
  shippingFee: number
  total: number
  paymentExpireMinutes: number              // 恒为 30
}
export interface OrderCreateResult { orderId: string; orderNo: string; status: OrderStatus; total: number; paymentExpireAt: string }
export interface PayOrderResult {
  orderId: string
  orderNo: string
  status: OrderStatus
  paymentMethod: string
  paidAt?: string
  qrCode?: string
  outTradeNo?: string
  expireAt?: string
}

// ===== 收藏 / 足迹 / 搜索历史 =====
export interface FavoriteItem { id: string; productId: string; createdAt: string; product?: Product }
export interface BrowseHistoryItem { id: string; productId: string; viewedAt: string; product?: Product }
export interface SearchHistoryItem { id: string; keyword: string; searchedAt: string }

// ===== 管理端 =====
export interface AdminShipmentResult { orderId: string; orderNo: string; status: OrderStatus; trackingNo: string; shippedAt: string }
export interface AdminUserStatusResult { userId: string; status: UserStatus; role: string }

// ===== 请求体 =====
export interface SmsRequest { phone: string; purpose?: string; captchaKey: string; captchaCode: string }
export interface LoginRequest { phone: string; smsCode: string }
export interface UserProfileUpdateRequest { nickname?: string; gender?: Gender; birthday?: string }
export interface AddressRequest {
  receiver: string; phone: string
  provinceCode?: string; province: string
  cityCode?: string; city: string
  districtCode?: string; district: string
  detail: string; isDefault?: boolean
}
export interface AddCartItemRequest { productId: string; quantity: number }
export interface UpdateCartItemRequest { quantity?: number; selected?: boolean }      // 至少一个
export interface CartSelectionRequest { selected: boolean }
export interface CartMergeItemRequest { productId: string; quantity: number; selected?: boolean }
export interface CartMergeRequest { items: CartMergeItemRequest[] }
export interface CartOrderPreviewRequest { cartItemIds: string[]; addressId: string }
export interface CartOrderCreateRequest { cartItemIds: string[]; addressId: string; buyerRemark?: string }
export interface DirectOrderCreateRequest { productId: string; quantity: number; addressId: string; buyerRemark?: string }
export interface PayOrderRequest { paymentMethod: 'mock' | 'alipay' }
export interface CancelOrderRequest { reason?: string }
export interface FavoriteRequest { productId: string }
export interface BrowseHistoryRequest { productId: string }
export interface SearchHistoryRequest { keyword: string }
export interface IdBatchRequest { ids: string[] }
export interface ShipOrderRequest { trackingNo: string }
```

---
## 7. 接口明细

> 通用说明：除标注「公开」的接口外，均需 `Authorization: Bearer {Token}`。请求体字段如无特别说明均为必填；
> 所有 ID 字段传字符串。错误码只列该接口最可能出现的；全局错误码见第 5 节。

### A. 认证模块

#### A1. 获取图形验证码 `GET /api/auth/captcha`（公开）

无需参数。验证码 30 秒有效，刷新即重新获取。

响应 `data`（`CaptchaData`）：

```json
{
  "image": "data:image/png;base64,iVBORw0KGgo...",
  "captchaKey": "7f3c87e0-9b1a-4c5d-8e2f-1234567890ab"
}
```

- `image`：可直接放入 `<img src>` 的 PNG Base64。
- `captchaKey`：发送短信时必须回传。

#### A2. 发送短信验证码 `POST /api/auth/sms/send`（公开）

请求体（`SmsRequest`）：

```json
{ "phone": "13800138001", "purpose": "login", "captchaKey": "7f3c87e0-9b1a-4c5d-8e2f-1234567890ab", "captchaCode": "7K3M" }
```

- `purpose` 后端目前忽略，可省略。
- `captchaKey` 不存在/已过期 → `40400 图形验证码已过期`。
- ⚠️ **已知后端 bug**：图形验证码判断逻辑反转（正确验证码会报 `40000 图形验证码错误`），见第 12 节。
- 同一手机号 60 秒内重复发送 → `42900 验证码请求过于频繁`。

响应 `data`（`SmsInfo`）：

```json
{ "cooldown": 60, "expiresIn": 300 }
```

- 实际短信验证码固定为 `1234`。

#### A3. 登录/自动注册 `POST /api/auth/login`（公开）

请求体（`LoginRequest`）：

```json
{ "phone": "13800138001", "smsCode": "1234" }
```

规则：

- `smsCode` 必须是后端已“发送”过的验证码，当前固定 `1234`。
- 手机号不存在 → 自动注册（role=`USER`，昵称 `橙子用户{后4位}`）。
- 手机号已封禁（status=disabled）→ `40300 账号已被封禁`。
- 验证码错误/过期 → `40001 验证码错误或已过期`。

响应 `data`（`LoginData`，⚠️ 字段名大小写即此）：

```json
{ "Token": "eyJhbGciOiJIUzI1NiJ9.xxx.yyy", "expiresAt": "2026-09-09T00:00:00Z", "newUser": true }
```

- `Token`：JWT，**大写 T**；有效期 24 小时。后续请求头 `Authorization: Bearer {Token}`。
- `expiresAt`：UTC 字符串。
- `newUser`：是否本次新注册。

#### A4. 获取当前登录用户 `GET /api/auth/me`（需登录）

规则：从 Redis 会话读取用户信息；会话失效 → `40100 未登录或会话已过期`。

响应 `data`（`AuthMeUser`，⚠️ 返回的是实体，字段无省略规则，且 **id 是数字**）：

```json
{
  "id": 10001,
  "phone": "13800138001",
  "nickname": "橙子用户8001",
  "avatarUrl": null,
  "gender": 0,
  "birthday": null,
  "status": "active",
  "role": "USER",
  "lastLoginAt": null,
  "createdAt": null,
  "updatedAt": null,
  "deletedAt": null
}
```

> 提示：个人中心推荐使用 `GET /api/users/me`（ID 为字符串、字段更干净）。两接口都存在。

#### A5. 退出登录 `DELETE /api/auth/logout`（需登录）

无请求体。删除服务端会话，Token 立即失效。响应 `data` 为 `{}`。

---

### B. 分类模块

#### B1. 分类列表 `GET /api/categories`（公开）

无参数。只返回启用且未删除的分类。

响应 `data`（数组，元素 `Category`）：

```json
[
  { "id": "10001", "name": "数码", "eyebrow": "SMART LIFE", "color": "#e7f5ff", "icon": "Laptop", "isVirtual": false }
]
```

---

### C. 商品模块

#### C1. 商品列表 `GET /api/products`（公开，带 300 秒缓存）

Query 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | int | 否 | 1 | >=1 |
| `pageSize` | int | 否 | 12 | 1~50 |
| `keyword` | string | 否 | "" | 模糊匹配名称/副标题/描述 |
| `categoryId` | string | 否 | - | 分类 ID（数字字符串） |
| `sort` | string | 否 | `default` | `default` ｜ `price_asc` ｜ `sales_desc` |

规则：

- 只返回 `status=on_sale` 的商品。
- 不支持的 `sort` → `40000 不支持的商品排序方式`。
- `pageSize>50` → `40000 分页参数错误`。

响应 `data`（`PageData<Product>`）：

```json
{
  "list": [
    {
      "id": "90001", "name": "橙子蓝牙耳机", "subtitle": "降噪长续航",
      "categoryId": "10001", "images": ["https://example.com/1.jpg"],
      "videoUrl": null, "price": 199.00, "originalPrice": 299.00,
      "stock": 88, "sales": 1200, "rating": 4.7, "reviewCount": 86,
      "description": "……", "shippingFee": 0.00, "tags": ["热卖", "新品"]
    }
  ],
  "total": 1, "page": 1, "pageSize": 12, "hasMore": false
}
```

#### C2. 商品详情 `GET /api/products/{productId}`（公开，带 300 秒缓存）

- `{productId}` 为数字字符串。
- 商品不在售/不存在 → `40400 商品不存在`。

响应 `data`（`ProductDetail`）：

```json
{
  "product": { "id": "90001", "name": "橙子蓝牙耳机", "images": [], "price": 199.00, "stock": 88, "sales": 1200, "rating": 4.7, "reviewCount": 86, "shippingFee": 0.00, "tags": [] },
  "reviews": [ { "id": "50001", "productId": "90001", "userName": "橙子同学", "avatar": "https://i.pravatar.cc/100?img=47", "content": "音质很好", "rating": 5, "anonymous": false, "createdAt": "2026-09-05T12:40:00" } ],
  "reviewSummary": { "average": 4.7, "reviewCount": 86, "goodRate": 0.9, "allCount": 86, "goodCount": 80, "mediumCount": 4, "badCount": 2 }
}
```

- `reviews`：最新 **3 条**可见评价。

#### C3. 商品评价分页 `GET /api/products/{productId}/reviews`（公开，带 120 秒缓存）

Query 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `filter` | string | 否 | `all` | `all` ｜ `good`(>=4) ｜ `medium`(==3) ｜ `bad`(<=2) ｜ `media` |
| `sort` | string | 否 | `latest` | **目前仅支持 `latest`**，其他值 → `40000 当前仅支持 latest 排序` |
| `page` | int | 否 | 1 | >=1 |
| `pageSize` | int | 否 | 10 | 1~50 |

规则：

- `filter=media`：当前后端**恒返回空列表**（已知限制，见第 12 节）。
- 不支持的 `filter` → `40000 不支持的评价筛选条件`。
- 商品不存在/下架 → `40400 商品不存在`。

响应 `data`（`ProductReviewPage`，即分页结构 + `summary`）：

```json
{
  "list": [ { "id": "50001", "productId": "90001", "userName": "匿名用户", "avatar": null, "content": "不错", "rating": 5, "anonymous": true, "createdAt": "2026-09-05T12:40:00" } ],
  "total": 1, "page": 1, "pageSize": 10, "hasMore": false,
  "summary": { "average": 4.7, "reviewCount": 86, "goodRate": 0.9, "allCount": 86, "goodCount": 80, "mediumCount": 4, "badCount": 2 }
}
```

---

### D. 购物车模块（全部需登录，操作仅限本人数据）

#### D1. 获取购物车 `GET /api/cart`

响应 `data`（`Cart`）：

```json
{
  "items": [
    {
      "id": "70001", "productId": "90001", "quantity": 2, "selected": true,
      "product": { "id": "90001", "name": "橙子蓝牙耳机", "images": [], "price": 199.00, "stock": 88, "sales": 1200, "rating": 4.7, "reviewCount": 86, "shippingFee": 0.00, "tags": [] },
      "effectivePrice": 199.00, "shippingFee": 0.00
    }
  ],
  "selectedCount": 1, "subtotal": 398.00, "shippingFee": 0.00, "total": 398.00
}
```

金额汇总规则（**前端展示以服务端为准**）：

- `selectedCount`：**选中商品的行数**（不是数量之和）。
- `subtotal`：选中商品的 `price × quantity` 之和。
- `shippingFee`：选中商品的运费之和（每个商品行只计一次运费，数量不叠加，见第 8 节）。
- `total = subtotal + shippingFee`。
- 已下架商品：`product` 字段为 null（JSON 中省略），金额不计入。

#### D2. 加购 `POST /api/cart/items`

请求体（`AddCartItemRequest`）：

```json
{ "productId": "90001", "quantity": 1 }
```

规则：

- 商品必须 `on_sale`，否则 `40400 商品不存在或已下架`。
- `quantity<1` → `40000 quantity 必须大于等于 1`。
- 库存不足 → `40900 商品库存不足`。
- 已存在同商品 → 数量累加，再次校验库存。

响应 `data`：单个 `CartItem`（`D1` 中 items 的元素结构）。

#### D3. 修改购物车项 `PATCH /api/cart/items/{cartItemId}`

请求体（`UpdateCartItemRequest`，二选一或都传，**至少一个**）：

```json
{ "quantity": 3, "selected": false }
```

- 都为空 → `40000 至少提供 quantity 或 selected 字段`。
- 改数量时同样校验商品在售与库存。
- 购物车项不存在/不属于当前用户 → `40400 购物车商品不存在`。

响应 `data`：更新后的 `CartItem`。

#### D4. 删除购物车项 `DELETE /api/cart/items/{cartItemId}`

无请求体。成功 `data: {}`；不存在 → `40400 购物车商品不存在`。

#### D5. 全选/全不选 `PUT /api/cart/items/selection`

请求体（`CartSelectionRequest`）：

```json
{ "selected": true }
```

- `selected` 为 null → `40000 selected 字段不能为空`。
- 作用范围为当前用户购物车**全部**商品。成功 `data: {}`。

#### D6. 删除全部已选中商品 `DELETE /api/cart/items/selected`

无请求体。未选中任何商品时也是成功（`data: {}`）。

#### D7. 合并游客购物车 `POST /api/cart/merge`

登录后把本地游客购物车同步到服务端。请求体（`CartMergeRequest`）：

```json
{
  "items": [
    { "productId": "90001", "quantity": 1, "selected": true },
    { "productId": "90002", "quantity": 2 }
  ]
}
```

规则：

- 同一商品在 items 中重复 → 数量先合并再入库。
- 每个商品校验在售与库存，失败即整体报错（`40400` / `40900`）。
- 服务端已有该商品 → **数量累加**，`selected` 取本次传入值（缺省 true）。
- `selected` 缺省视为 `true`。

响应 `data`：合并后的完整 `Cart`（同 D1）。

---

### E. 订单模块（全部需登录）

#### E1. 购物车结算预览 `POST /api/orders/cart/preview`

请求体（`CartOrderPreviewRequest`）：

```json
{ "cartItemIds": ["70001", "70002"], "addressId": "40001" }
```

规则：

- `cartItemIds` 必须都属于当前用户且都存在，否则 `40400 购物车商品不存在`。
- `addressId` 必须属于当前用户，否则 `40400 地址不存在`。
- 校验商品在售与库存（不扣减库存，仅校验）。

响应 `data`（`OrderPreview`）：

```json
{
  "items": [
    { "id": null, "productId": "90001", "productName": "橙子蓝牙耳机", "productImage": "https://example.com/1.jpg", "unitPrice": 199.00, "quantity": 2, "lineAmount": 398.00 }
  ],
  "address": { "receiver": "张三", "phone": "138****0001", "province": "北京市", "city": "北京市", "district": "朝阳区", "detail": "xx路1号" },
  "subtotal": 398.00, "shippingFee": 0.00, "total": 398.00,
  "paymentExpireMinutes": 30
}
```

> 注意：预览中的 `items[].id` 为 `null`（JSON 中省略），下单后才有真实 ID。

#### E2. 购物车下单 `POST /api/orders/cart`

请求体（`CartOrderCreateRequest`）：

```json
{ "cartItemIds": ["70001"], "addressId": "40001", "buyerRemark": "请尽快发货" }
```

- `buyerRemark` 可省略。
- 会**扣减库存**（条件更新：在售且库存充足，失败回滚 → `40900 商品库存不足`）。
- 下单成功后**删除**对应用户的这组购物车项。
- 订单初始状态 `pending_payment`，支付超时 30 分钟（RabbitMQ 延迟自动取消）。

响应 `data`（`OrderCreateResult`）：

```json
{ "orderId": "60001", "orderNo": "OM2026090812000012345", "status": "pending_payment", "total": 398.00, "paymentExpireAt": "2026-09-08T12:30:00" }
```

#### E3. 立即购买（直接下单）`POST /api/orders/direct`

请求体（`DirectOrderCreateRequest`）：

```json
{ "productId": "90001", "quantity": 1, "addressId": "40001", "buyerRemark": "" }
```

规则同 E2（不经过购物车，不影响购物车）。响应同 E2。

#### E4. 订单列表 `GET /api/orders`

Query 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
| --- | --- | --- | --- | --- |
| `status` | string | 否 | - | 见 `OrderStatus`；为空返回全部 |
| `page` | int | 否 | 1 | >=1 |
| `pageSize` | int | 否 | 10 | 1~50 |

- 非法 status → `40000 订单状态参数错误`。
- 按创建时间倒序。每个订单带 `items`。

响应 `data`（`PageData<Order>`，元素结构见第 6 节 `Order`）：

```json
{
  "list": [
    {
      "id": "60001", "orderNo": "OM2026090812000012345", "status": "pending_payment",
      "items": [ { "id": "80001", "productId": "90001", "productName": "橙子蓝牙耳机", "productImage": "https://example.com/1.jpg", "unitPrice": 199.00, "quantity": 2, "lineAmount": 398.00 } ],
      "address": { "receiver": "张三", "phone": "138****0001", "province": "北京市", "city": "北京市", "district": "朝阳区", "detail": "xx路1号" },
      "subtotal": 398.00, "shippingFee": 0.00, "total": 398.00,
      "buyerRemark": null, "paymentMethod": null, "trackingNo": null,
      "createdAt": "2026-09-08T12:00:00", "paymentExpireAt": "2026-09-08T12:30:00",
      "paidAt": null, "shippedAt": null, "receivedAt": null, "completedAt": null, "cancelledAt": null
    }
  ],
  "total": 1, "page": 1, "pageSize": 10, "hasMore": false
}
```

#### E5. 订单详情 `GET /api/orders/{orderId}`

- 只能获取本人订单，否则 `40400 订单不存在`。

响应 `data`：单个 `Order`（同 E4 元素）。

#### E6. 支付订单 `POST /api/orders/{orderId}/pay`

请求体（`PayOrderRequest`）：

```json
{ "paymentMethod": "alipay" }
```

规则：

- `paymentMethod` 必须为 `alipay` 或 `mock`（`mock` 仅当服务端 `ALIPAY_ALLOW_MOCK=true`）。
- 仅 `pending_payment` 可支付，否则 `42200 仅待付款订单可以支付`。
- 超过 `paymentExpireAt` → `42200 订单支付已超时`。
- `alipay`：调用当面付预下单，订单仍为 `pending_payment`，返回 `qrCode`（二维码码串，前端自行绘制）和 `outTradeNo`。**不要**把此次响应当成已付款。
- 支付完成后订单变为 `pending_shipment`（异步通知或 `POST /api/orders/{orderId}/payment/sync`）。前端应轮询 `GET /api/orders/{orderId}`，或提供「我已支付」调用 sync。
- `mock`：同步成功，状态 → `pending_shipment`，写入 `paidAt`。

支付宝预下单响应 `data`（`PayOrderResult`）：

```json
{
  "orderId": "60001",
  "orderNo": "OM2026090812000012345",
  "status": "pending_payment",
  "paymentMethod": "alipay",
  "paidAt": null,
  "qrCode": "https://qr.alipay.com/baxxxx",
  "outTradeNo": "P60001T1758440000ABCD1234",
  "expireAt": "2026-09-08T12:30:00"
}
```

#### E6b. 主动查单 `POST /api/orders/{orderId}/payment/sync`

无请求体，需登录。向支付宝查单，已支付则与异步通知同一套落库。用于回调未到达时确认付款。返回同 `PayOrderResult`。

#### E7. 取消订单 `POST /api/orders/{orderId}/cancel`

请求体（`CancelOrderRequest`）：

```json
{ "reason": "不想要了" }
```

- `reason` 可省略。
- 仅 `pending_payment` 可取消，否则 `42200 仅待付款订单可以取消`。
- 取消后自动恢复库存。成功 `data: {}`。
- 支付超时由后端自动取消（`cancelReason = 支付超时自动取消`），前端无需处理。

#### E8. 确认收货 `POST /api/orders/{orderId}/receive`

无请求体。规则：

- 仅 `pending_receipt` 可确认，否则 `42200 仅待收货订单可以确认收货`。
- 成功后状态 → `pending_review`，写入 `receivedAt`。成功 `data: {}`。

---

### F. 用户资料与地址（全部需登录）

#### F1. 个人资料 `GET /api/users/me`

响应 `data`（`UserProfile`，见第 6 节）：

```json
{ "id": "10001", "phone": "13800138001", "nickname": "橙子用户8001", "avatarUrl": null, "gender": 0, "birthday": "1998-08-18", "status": "active", "role": "USER", "lastLoginAt": "2026-09-08T10:00:00" }
```

#### F2. 修改资料 `PATCH /api/users/me`

请求体（`UserProfileUpdateRequest`，三个字段至少一个）：

```json
{ "nickname": "橙子同学", "gender": 1, "birthday": "1998-08-18" }
```

- 全部为 null → `40000 至少提供 nickname、gender 或 birthday 字段`。
- `nickname` 不能为空白 → `40000 nickname 不能为空`。
- `gender` 范围 0~2，否则 `40000 gender 参数错误`。
- 修改资料会同步更新服务端会话里的用户信息。

响应 `data`：更新后的 `UserProfile`。

#### F3. 地址列表 `GET /api/users/me/addresses`

响应 `data`（数组，元素 `Address`，按创建时间倒序；**phone 已脱敏**）：

```json
[
  { "id": "40001", "receiver": "张三", "phone": "138****0001", "provinceCode": "110000", "province": "北京市", "cityCode": "110100", "city": "北京市", "districtCode": "110101", "district": "朝阳区", "detail": "xx路1号", "isDefault": true }
]
```

#### F4. 新增地址 `POST /api/users/me/addresses`

请求体（`AddressRequest`）：

```json
{
  "receiver": "张三", "phone": "13800138001",
  "provinceCode": "110000", "province": "北京市",
  "cityCode": "110100", "city": "北京市",
  "districtCode": "110101", "district": "朝阳区",
  "detail": "xx路1号", "isDefault": true
}
```

规则：

- `receiver`、`phone`、`province`、`city`、`district`、`detail` 必填（去空格后非空）。
- `phone` 须匹配 `1\d{10}`，否则 `40002 手机号格式错误`。
- `provinceCode/cityCode/districtCode` 可省略。
- `isDefault=true` 时，会把其他地址默认标记清除（每用户只有一个默认地址）。

响应 `data`：新建的 `Address`（已脱敏 phone）。

#### F5. 修改地址 `PUT /api/users/me/addresses/{addressId}`

请求体同 F4。规则：只能改本人的地址，否则 `40400 地址不存在`；字段校验同 F4。`isDefault` 只在传入时生效（传 `false` 会取消默认标记，但不会自动指定新默认）。

响应 `data`：更新后的 `Address`。

#### F6. 删除地址 `DELETE /api/users/me/addresses/{addressId}`

- 删除的是默认地址时，会自动把最新一条地址设为默认（没有则无默认地址）。成功 `data: {}`。
- 不存在 → `40400 地址不存在`。

#### F7. 设为默认地址 `PUT /api/users/me/addresses/{addressId}/default`

无请求体。清除其他默认标记并把该地址设为默认。响应 `data`：该 `Address`。

---

### G. 收藏 / 足迹 / 搜索历史（全部需登录）

#### G1. 收藏列表 `GET /api/users/me/favorites`

Query：`page`(默认 1)、`pageSize`(默认 20，最大 50)。

响应 `data`（`PageData<FavoriteItem>`）：

```json
{
  "list": [ { "id": "30001", "productId": "90001", "createdAt": "2026-09-08T10:00:00", "product": { "id": "90001", "name": "橙子蓝牙耳机", "images": [], "price": 199.00, "stock": 88, "sales": 1200, "rating": 4.7, "reviewCount": 86, "shippingFee": 0.00, "tags": [] } } ],
  "total": 1, "page": 1, "pageSize": 20, "hasMore": false
}
```

- 商品已删除时 `product` 省略。

#### G2. 添加收藏 `POST /api/users/me/favorites`

请求体（`FavoriteRequest`）：`{ "productId": "90001" }`

- 重复收藏幂等（返回已存在的收藏）。商品不存在 → `40400 商品不存在`。

响应 `data`：`FavoriteItem`（含 product）。

#### G3. 取消收藏 `DELETE /api/users/me/favorites/{productId}`

- `{productId}` 为商品 ID。幂等，不存在也返回成功。成功 `data: {}`。

#### G4. 浏览足迹列表 `GET /api/users/me/browse-history`

Query：`page`(默认 1)、`pageSize`(默认 20，最大 50)。按浏览时间倒序。

响应 `data`（`PageData<BrowseHistoryItem>`）：

```json
{ "list": [ { "id": "20001", "productId": "90001", "viewedAt": "2026-09-08T10:00:00", "product": { ...Product } } ], "total": 1, "page": 1, "pageSize": 20, "hasMore": false }
```

#### G5. 记录浏览足迹 `POST /api/users/me/browse-history`

请求体（`BrowseHistoryRequest`）：`{ "productId": "90001" }`

- 同一商品重复浏览 → 更新 `viewedAt`（不产生重复记录）。

响应 `data`：`BrowseHistoryItem`。

#### G6. 删除单条足迹 `DELETE /api/users/me/browse-history/{historyId}`

`{historyId}` 是**足迹记录 ID**（不是商品 ID）。不存在 → `40400 浏览足迹不存在`。成功 `data: {}`。

#### G7. 清空足迹 `DELETE /api/users/me/browse-history`

无参数，幂等。成功 `data: {}`。

#### G8. 批量删除足迹 `POST /api/users/me/browse-history/batch-delete`

请求体（`IdBatchRequest`）：

```json
{ "ids": ["20001", "20002"] }
```

- `ids` 为空数组 → 静默成功；字段缺失 → `40000 ids 字段不能为空`。成功 `data: {}`。

#### G9. 搜索历史列表 `GET /api/users/me/search-history`

Query：`page`(默认 1)、`pageSize`(默认 10，最大 50)。按搜索时间倒序。

响应 `data`（`PageData<SearchHistoryItem>`）：

```json
{ "list": [ { "id": "10001", "keyword": "耳机", "searchedAt": "2026-09-08T10:00:00" } ], "total": 1, "page": 1, "pageSize": 10, "hasMore": false }
```

#### G10. 记录搜索 `POST /api/users/me/search-history`

请求体（`SearchHistoryRequest`）：`{ "keyword": "耳机" }`

- `keyword` 必填非空。重复关键词 → 更新时间。

响应 `data`：`SearchHistoryItem`。

#### G11. 删除单条搜索 `DELETE /api/users/me/search-history/{historyId}`

不存在 → `40400 搜索历史不存在`。成功 `data: {}`。

#### G12. 清空搜索历史 `DELETE /api/users/me/search-history`

无参数，幂等。成功 `data: {}`。

---

### H. 评价模块（全部需登录）

#### H1. 待评价列表 `GET /api/users/me/reviews/pending`

无参数。返回所有 `pending_review` 订单中**尚未评价**的商品行（按订单创建时间倒序、订单内商品正序；已评价过的行不再出现）。

响应 `data`（数组，元素 `PendingReviewItem`）：

```json
[
  {
    "orderId": "60002", "orderNo": "OM2026090812000012346", "orderItemId": "80002",
    "productId": "90001", "productName": "橙子蓝牙耳机", "productImage": "https://example.com/1.jpg",
    "unitPrice": 199.00, "quantity": 1, "lineAmount": 199.00
  }
]
```

#### H2. 提交评价 `POST /api/orders/{orderId}/reviews`

请求体（`SubmitReviewsRequest`）：

```json
{
  "reviews": [
    { "orderItemId": "80002", "productId": "90001", "rating": 5, "content": "音质很好，值得购买", "anonymous": false }
  ]
}
```

规则：

- `reviews` 不能为空；`orderItemId` 不能重复。
- `rating` 必须 1~5，否则 `40000 rating 必须在 1 到 5 之间`。
- `content` 必填（去空格后非空），否则 `40000 content 不能为空`。
- 订单必须属于当前用户且状态为 `pending_review`，否则 `40400 订单不存在` / `42200 当前订单状态不允许评价`。
- `productId` 必须与该订单行一致，否则 `40000 productId 与订单商品不一致`。
- 同一订单行重复评价 → `40900 订单商品不能重复评价`。
- 该订单**所有**商品行都评价完成后，订单自动变 `completed` 并写入 `completedAt`；否则保持 `pending_review`。

响应 `data`（`ReviewSubmissionResult`）：

```json
{ "orderId": "60002", "orderNo": "OM2026090812000012346", "status": "completed", "reviewIds": ["50002"], "completedAt": "2026-09-08T12:40:00" }
```

- 若还有未评价行，`status` 为 `pending_review`、`completedAt` 省略。

---

### I. 管理端（需 `admin` 或 `ADMIN` 角色，普通用户 → `40300 无权访问该资源`）

#### I1. 订单发货 `POST /api/admin/orders/{orderId}/ship`

请求体（`ShipOrderRequest`）：

```json
{ "trackingNo": "SF1234567890" }
```

规则：

- `trackingNo` 必填且 ≤128 字符，否则 `40000`。
- 仅 `pending_shipment` 可发货，否则 `42200 仅待发货订单可以发货`。
- 成功后状态 → `pending_receipt`，写入 `trackingNo`/`shippedAt`。

响应 `data`（`AdminShipmentResult`）：

```json
{ "orderId": "60001", "orderNo": "OM2026090812000012345", "status": "pending_receipt", "trackingNo": "SF1234567890", "shippedAt": "2026-09-08T13:00:00" }
```

#### I2. 封禁用户 `PUT /api/admin/users/{userId}/ban`

无请求体。规则：

- 把用户 `status` 置为 `disabled`，删除该用户全部登录会话（其 Token 立即失效）。
- 不能封禁管理员账号 → `40300 不能封禁管理员账号`。
- 已封禁用户重复封禁幂等成功。

响应 `data`（`AdminUserStatusResult`）：

```json
{ "userId": "10002", "status": "disabled", "role": "USER" }
```

> 说明：后端没有「管理员登录/注册」接口，管理员账号通过数据库维护（`user_account.role = 'admin'`）。

---

## 8. 订单状态机与前端交互规则

### 8.1 状态流转（后端强制校验）

```
创建订单 → pending_payment ──支付(alipay/mock)──→ pending_shipment ──管理员发货──→ pending_receipt
                              │                                            │
                              │ 用户取消 / 30分钟超时自动取消             用户确认收货
                              ↓                                            ↓
                           cancelled                                   pending_review
                                                                            │
                                                             全部商品行评价完成
                                                                            ↓
                                                                        completed
```

另外存在 `refunding` / `refunded`（售后预留状态，当前无接口触发）。

### 8.2 各状态前端可执行操作

| 状态 | 前端操作 | 对应接口 |
| --- | --- | --- |
| `pending_payment` | 去支付 / 取消订单；显示支付倒计时（用 `paymentExpireAt`） | E6 / E7 |
| `pending_shipment` | 等待发货，展示支付方式 | - |
| `pending_receipt` | 确认收货；展示 `trackingNo` | E8 |
| `pending_review` | 去评价（入口数据来自 H1） | H2 |
| `completed` | 无操作（可再次查看、评价已提交） | - |
| `cancelled` | 无操作 | - |

### 8.3 金额与运费规则（服务端计算，前端只展示）

- 商品行小计 `lineAmount = unitPrice × quantity`。
- 运费：**每个商品行（不同商品）计一次 `shippingFee`，同商品数量不叠加运费**。
- `subtotal = Σ lineAmount`；`shippingFee = Σ 各行运费`；`total = subtotal + shippingFee`。
- 购物车预览、订单预览、订单详情中的金额均为服务端结果；前端不要自行重算。

### 8.4 支付与超时

- 支付方式：`alipay`（当面付扫码）或本地 `mock`。
- 订单创建后 **30 分钟**内必须支付（`paymentExpireAt` / `paymentExpireMinutes=30`）。
- `alipay` 预下单后请展示 `qrCode`；付款结果以后端订单状态为准（异步通知或 `payment/sync`）。
- 超时由后端 RabbitMQ 延迟任务自动取消（先向支付宝查单/关单；已支付则落库不取消）。
- 前端收到 422「订单支付已超时 / 订单状态已发生变化」时刷新订单列表。

---

## 9. 购物车与游客购物车同步

- 未登录：建议把购物车保存在 localStorage（示例 key：`orange_market_guest_cart`），结构为 `CartMergeItemRequest[]`。
- 登录成功后调用 `POST /api/cart/merge` 上传本地购物车，然后清空本地。
- 服务端购物车以**商品**为粒度：同一商品只占一条记录（数量累加）。
- 加购/改数量/下单均以服务端返回为准；商品下架或库存不足时接口会直接报错，前端给出提示并刷新购物车。

---

## 10. 页面 → 接口 映射表

| 前端页面（参考现有 Frontend 项目） | 主要接口 |
| --- | --- |
| 首页 HomePage | B1 分类、C1 商品列表 |
| 分类/搜索页 CatalogPage | B1、C1（keyword/categoryId/sort） |
| 商品详情页 ProductPage | C2 详情、C3 评价分页、D2 加购、G2 收藏（登录后） |
| 购物车页 CartPage | D1、D3、D4、D5、D6 |
| 结算页 CheckoutPage | F3 地址列表、E1 预览、E2/E3 下单 |
| 支付页 PaymentPage | E6 支付、E4 订单刷新 |
| 订单列表页 OrdersPage | E4（按状态 tab 过滤） |
| 订单详情页 OrderDetailPage | E5、E7 取消、E8 收货 |
| 评价页 ReviewsPage | H1 待评价、H2 提交 |
| 个人中心 ProfilePage / AccountPage | A4 或 F1/F2、A5 退出 |
| 地址管理 AddressesPage | F3~F7 |
| 收藏页 FavoritesPage | G1、G2、G3 |
| 足迹页 HistoryPage | G4~G8 |
| 登录页 LoginPage | A1 验证码、A2 短信、A3 登录、D7 合并购物车 |
| 客服/服务页 ServicePage | 见独立文档 `FRONTEND_CUSTOMER_SERVICE_DOC.md`（REST + WebSocket） |

---

## 11. 前端实现建议（API 封装示例）

```ts
// src/lib/http.ts —— 基于 axios 的示例
import axios, { AxiosError } from 'axios'
import type { ApiResult } from '../types'

export const http = axios.create({ baseURL: '/api', timeout: 10000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('orange_market_token')   // 登录时存 LoginData.Token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResult<unknown>
    // 业务失败：把 message 交给调用方/全局 toast
    if (body && typeof body.code === 'number' && body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return response
  },
  (error: AxiosError<ApiResult<unknown> | { code?: number; message?: string }>) => {
    const status = error.response?.status
    const code = error.response?.data?.code
    if (status === 401 || code === 401) {
      // 认证失效：清登录态并跳转登录页（注意 401 响应体可能是 {code:401,message} 或标准 Result）
      localStorage.removeItem('orange_market_token')
      localStorage.removeItem('orange_market_user')
      if (!location.pathname.startsWith('/login')) {
        location.href = `/login?redirect=${encodeURIComponent(location.pathname + location.search)}`
      }
      return Promise.reject(error)
    }
    const msg = error.response?.data?.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(msg))
  }
)

// 标准用法：const { data } = await http.post<ApiResult<LoginData>>('/auth/login', body)
// 成功时 data.code === 0，业务数据在 data.data
```

建议在调用层统一做一次 `unwrap`：`const res = await http.post(...); return res.data.data`。

### 分页加载（无限滚动）要点

- 用响应里的 `page` / `pageSize` / `hasMore` / `total` 驱动。
- 刷新/切换筛选时重置 `page=1`；`hasMore=false` 时停止加载。
- 商品列表注意服务端有缓存：修改数据后可能 5 分钟内不更新。

### 登录态与用户信息

- 登录后存：`orange_market_token`（LoginData.Token）、`orange_market_user`（UserProfile 或 AuthMeUser）、`orange_market_login_expires_at`。
- 用 `expiresAt` 判断是否需要强制重新登录。
- 页面鉴权：路由守卫里没有 token 时重定向到 `/login?redirect=...`。

---

## 12. 后端已知问题与注意事项

1. **图形验证码判断反转（bug）**：`AuthService.sendSms` 中提交**正确**验证码会返回 `40000 图形验证码错误`，提交错误验证码反而放行。前端仍按正常逻辑实现；后端需把 `s.equals(smsRequest.getCaptchaCode())` 改为 `!s.equals(...)` 后联调。
2. **短信验证码固定 1234**：`auth:sms:1234` 是所有手机号共享的验证码键，有效期 300 秒；发送接口频率限制 60 秒/手机号。
3. **`filter=media` 恒返回空列表**：商品评价接口的 `media` 筛选当前后端直接返回空 list（total=0），前端可隐藏该筛选项，或不展示图片筛选。
4. **认证失败响应体特殊**：HTTP 401 时响应体是 `{code:401,message}`，没有 `data/timestamp`；业务层 401 时是标准 Result（`code=40100`）。前端两种都要兼容。
5. **空成功 data 是 `{}`**：无内容接口（删除/收货/取消等）`data` 为 `{}`，不是 null。
6. **`/api/auth/me` 返回原始实体**：`id` 是数字、包含 `createdAt/updatedAt/deletedAt`、null 字段不省略；与 `/api/users/me` 的 DTO 结构不同，前端按各自类型处理。
7. **金额展示**：金额是 JSON number（保留两位小数）；前端用 `toFixed(2)` 展示，避免浮点误差。
8. **时间格式**：业务时间无时区后缀（服务端本地时间 Asia/Shanghai）；`expiresAt` 带 `Z`（UTC）。展示倒计时/相对时间时注意两者差异。
9. **地址 phone 脱敏**：所有地址接口返回的 `phone` 是 `138****0001` 形式，仅展示用；下单时后端读取的是库中完整手机号。
10. **收货地址快照**：下单后订单地址是快照，之后修改/删除地址不影响已下订单。
11. **分页上限**：所有列表 `pageSize` 最大 50；超出报 40000。
12. **管理端**：无登录注册接口，管理员账号由数据库维护（role=`admin`/`ADMIN`）。
13. **评价与订单完成**：订单 `pending_review` 时必须把该订单所有商品行评价完才会变 `completed`，可多次提交（不同行）。
14. **游客购物车合并时机**：建议在登录成功、跳转回原页面前调用 `POST /api/cart/merge`，失败不阻塞登录（给出提示后继续）。

---

*文档版本：v1.0（2026-09-08）｜来源：OrangeMarketBackend 当前源码（已序列化验证）*
