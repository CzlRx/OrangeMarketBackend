# 橙子商城人工客服前端对接文档

> 依据当前后端源码整理，可直接交给前端实现用户客服页与客服工作台。  
> 约定：**会话状态和历史走 REST，实时聊天走 WebSocket**。  
> ID 一律按 **字符串** 处理。本版无附件、无「正在输入」、无独立客服角色（客服复用 `admin`）。

---

## 1. 环境

| 项 | 值 |
| --- | --- |
| HTTP | `http://localhost:8080` |
| WebSocket | `ws://localhost:8080/ws/service?token={jwt}` |
| REST 前缀 | `/api` |
| 鉴权 | `Authorization: Bearer {jwt}` |

本地 Vite 建议同时代理 HTTP 与 WebSocket：

```ts
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
```

前端连 WS 可用相对环境：`ws://${location.host}/ws/service?token=${token}`（开发走代理时）。

---

## 2. 角色与权限

| 端 | 账号 role | 说明 |
| --- | --- | --- |
| 用户客服页 | `USER` | 调用 `/api/service/**` |
| 客服工作台 | `admin` / `ADMIN` | 调用 `/api/admin/service/**`；WS 握手后 `agent=true` |

普通用户调管理端接口返回业务码 `40300`。客服 JWT 若 Redis 里 role 不是 admin，WS 的 `connected.agent` 为 `false`。

---

## 3. 统一响应与错误码

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1788601800000
}
```

成功 `code === 0`。失败 `data` 为 `null`。结束会话成功时 `data` 为 `{}`。

| code | HTTP | 客服场景常见含义 |
| --- | --- | --- |
| 0 | 200 | 成功 |
| 40000 | 400 | 参数不合法（分页、空消息、超 2000 字） |
| 40100 | 401 | 未登录或 Token / Redis 会话无效 |
| 40300 | 403 | 无权（非 admin、看别人的会话、关别人接的单） |
| 40400 | 404 | 会话不存在 / 当前没有进行中会话 |
| 40900 | 409 | 该会话已被其他客服接单 |
| 42200 | 422 | 会话已关闭，或客服未接单就回复 |

`message` 以服务端返回为准，常见文案：

| code | message |
| --- | --- |
| 40000 | `分页参数不合法` / `消息内容不能为空` / `消息内容不能超过2000字` / `会话 ID 不合法` |
| 40400 | `当前没有进行中的客服会话` / `客服会话不存在` |
| 40300 | `无权访问该客服会话` / `无权查看该会话消息` / `只能关闭自己接待的会话` / `无权在该会话中发言` |
| 40900 | `该会话已被其他客服接单` |
| 42200 | `会话已关闭` / `请先接单再回复` / `会话状态已变化，请刷新后重试` |

分页：`page` 从 `1` 开始，默认 `pageSize=20`，最大 `50`。

```json
{
  "list": [],
  "total": 0,
  "page": 1,
  "pageSize": 20,
  "hasMore": false
}
```

消息列表按 `id` **升序**（旧 → 新）。

---

## 4. 数据模型

所有 `id` / `userId` / `agentId` / `messageId` 均为**字符串**（雪花 ID，不要用 `Number` 接）。  
`null` 字段不会出现在 JSON 里（`@JsonInclude(NON_NULL)`）。  
REST 时间为 Jackson 序列化的 `LocalDateTime`，形如 `2026-09-12T16:50:58`；WS 的 `createdAt` 可能带纳秒小数。

### 4.1 会话 `ServiceSession`

```ts
type SessionStatus = 'active' | 'closed'

interface ServiceSession {
  id: string
  userId: string
  userNickname?: string
  agentId?: string | null  // 未接单时省略该字段
  status: SessionStatus
  createdAt: string
  closedAt?: string | null  // 未关闭时省略
}
```

### 4.2 消息 `ServiceMessage`

```ts
type SenderType = 'user' | 'agent' | 'system'

interface ServiceMessage {
  id: string
  sessionId: string
  senderType: SenderType
  senderId?: string | null  // 系统消息可能为空
  content: string
  createdAt: string
}
```

### 4.3 分页

```ts
interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
  hasMore: boolean
}
```

---

## 5. 页面流程

### 5.1 用户客服页

1. `POST /api/service/sessions` 创建或复用进行中会话，得到 `sessionId`。  
2. `GET /api/service/sessions/{id}/messages` 拉历史。  
3. 连接 `ws://.../ws/service?token={jwt}`。  
4. 收到 `connected` 后可 `ping` / 发 `chat`。  
5. 结束：`POST /api/service/sessions/{id}/close`（不要只关 WS；关 WS 只表示离开，会话仍是 `active`）。

同一用户同一时间只有一条进行中会话；重复 POST 会返回同一条。

### 5.2 客服工作台

1. 进入页面即连 WS（`agent` 应为 `true`）。  
2. **大厅不会实时推新单**，需轮询或手动刷新：`GET /api/admin/service/sessions/lobby`（建议 3～5 秒）。  
3. 接单：`POST /api/admin/service/sessions/{id}/claim`。  
4. 「我的会话」：`GET /api/admin/service/sessions/mine`。  
5. 拉消息 + WS 回复；结束走 REST close。

同一账号客服 WS 只保留一条，再次连接会顶掉旧连接。

---

## 6. 用户 REST `/api/service`

均需登录。无特殊说明则无请求体。

请求头（所有 REST）：

```http
Authorization: Bearer {jwt}
Content-Type: application/json
```

### 6.1 创建或获取进行中会话

`POST /api/service/sessions`

响应 `data` 为 `ServiceSession`。未接单时没有 `agentId`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "11",
    "userId": "10001",
    "userNickname": "橙子用户8001",
    "status": "active",
    "createdAt": "2026-09-12T16:50:58"
  },
  "timestamp": 1788601800000
}
```

### 6.2 当前进行中会话

`GET /api/service/sessions/current`

没有进行中会话：`40400`，「当前没有进行中的客服会话」。

### 6.3 会话详情

`GET /api/service/sessions/{sessionId}`

只能看自己的会话。

### 6.4 历史消息

`GET /api/service/sessions/{sessionId}/messages?page=1&pageSize=20`

响应 `data` 为 `PageResult<ServiceMessage>`。列表按 `id` 升序（旧 → 新）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "53",
        "sessionId": "11",
        "senderType": "user",
        "senderId": "10001",
        "content": "你好",
        "createdAt": "2026-09-12T16:50:58"
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20,
    "hasMore": false
  },
  "timestamp": 1788601800000
}
```

### 6.5 用户结束会话

`POST /api/service/sessions/{sessionId}/close`

成功时 REST 的 `data` 为空对象。双方 WS 会收到一条 `session_closed`（见第 8 节）。

---

## 7. 客服 REST `/api/admin/service`

需要 `role` 为 `admin` 或 `ADMIN`。

### 7.1 待接大厅

`GET /api/admin/service/sessions/lobby?page=1&pageSize=20`

条件：`status=active` 且无 `agentId`，按创建时间升序。

### 7.2 我已接待的进行中会话

`GET /api/admin/service/sessions/mine?page=1&pageSize=20`

### 7.3 接单

`POST /api/admin/service/sessions/{sessionId}/claim`

- 成功：返回更新后的会话（带 `agentId`）。  
- 已被别人接：`40900`。  
- 已关闭：`42200`。  
- 自己重复点接单：直接返回当前会话。

接单成功后，会话双方 WS **连续收到两条**：先 `session_claimed`，再一条系统 `chat`（`content` 为「客服已接入」）。请分别处理，不要当成重复结束。

未接单时客服也可预览消息（7.4）。`mine` 按最近更新时间倒序。

### 7.4 历史消息

`GET /api/admin/service/sessions/{sessionId}/messages?page=1&pageSize=20`

已接单后只能看自己接待的会话；未接单的大厅会话可以预览。

### 7.5 客服结束会话

`POST /api/admin/service/sessions/{sessionId}/close`

只能关闭自己接待的会话，否则 `40300`。

---

## 8. WebSocket 协议

### 8.1 连接

```text
ws://{host}/ws/service?token={jwt}
```

不要把完整 `ws://host/...` 再拼进「路径」里。浏览器原生 WebSocket **必须用 query `token`**；也兼容握手头 `Authorization: Bearer {jwt}`。握手失败不会进入 101，连接直接被拒绝。

握手成功后服务端推送：

```json
{
  "type": "connected",
  "userId": 10001,
  "agent": false
}
```

客服账号应为 `"agent": true`。建议 20～30 秒发一次心跳。

同一用户（或同一客服）新连接会顶掉旧连接。

### 8.2 前端 → 服务端

心跳：

```json
{ "type": "ping" }
```

发消息（`sessionId` 用字符串或数字均可；`content` 去空格后 1～2000 字）：

```json
{
  "type": "chat",
  "sessionId": "11",
  "content": "你好，我想问一下订单"
}
```

规则：

- 用户可在未接单时发送（入库；客服接单后拉历史可见）。  
- 客服未接单就发：WS `error`，「请先接单再回复」。  
- 会话已关闭再发：`error`，「会话已关闭」。  
- 不是该会话成员：`error` 无权。

### 8.3 服务端 → 前端（按 `type` 分支）

所有实时帧都是 JSON。聊天列表请用 `messageId` 去重（先拉历史再连 WS 时尤其需要）。

#### `pong`

```json
{ "type": "pong" }
```

#### `chat`

普通聊天或系统提示（进入/离开/重新进入、客服已接入等）。

```json
{
  "type": "chat",
  "sessionId": "11",
  "messageId": "53",
  "senderType": "system",
  "content": "用户已进入会话",
  "createdAt": "2026-09-12T16:50:58.238639300"
}
```

有发送人时带 `senderId`。`senderType` 为 `user` | `agent` | `system`。

系统文案（展示用，不要写死业务状态，以 `senderType=system` + 文案为准）：

| content | 含义 |
| --- | --- |
| 用户已进入会话 | 用户首次连上（且已有进行中会话） |
| 用户已离开会话 | 用户 WS 全部断开约 0.8s 后 |
| 用户已重新进入会话 | 离开后再连上 |
| 客服已进入会话 | 客服首次连上（且已有自己接待的进行中会话） |
| 客服已离开会话 | 客服 WS 全部断开约 0.8s 后 |
| 客服已重新进入会话 | 离开后再连上 |
| 客服已接入 | 客服点了接单 |

闪断（0.8s 内重连）可能不出现离开/回来。

**结束会话不会再推一条 `chat`。** 结束只推 `session_closed`。历史接口里仍有对应系统消息。

#### `session_claimed`

```json
{
  "type": "session_claimed",
  "sessionId": "11",
  "agentId": "10004"
}
```

用户侧：显示已接入，可记下 `agentId`。  
客服侧：把该单从大厅挪到「我的会话」。

#### `session_closed`

实时通道结束会话时 **只推这一条**（不要按两条消息渲染）。

```json
{
  "type": "session_closed",
  "sessionId": "11",
  "closedBy": "agent",
  "closerId": "10004",
  "messageId": "53",
  "senderType": "system",
  "content": "客服已结束会话",
  "createdAt": "2026-09-12T16:50:58.238639300"
}
```

| 字段 | 说明 |
| --- | --- |
| `closedBy` | `user` 用户结束；`agent` 客服结束 |
| `content` | `用户已结束会话` / `客服已结束会话` |
| `messageId` | 可插入消息列表，与历史里的系统消息对应 |

前端：展示 `content`、禁用输入、将会话标为 `closed`。

#### `error`

```json
{
  "type": "error",
  "message": "会话已关闭"
}
```

只提示，不要断开 WS。

---

## 9. TypeScript 事件联合类型（可直接用）

```ts
type WsClientSend =
  | { type: 'ping' }
  | { type: 'chat'; sessionId: string; content: string }

type WsServerEvent =
  | { type: 'connected'; userId: number; agent: boolean }
  | { type: 'pong' }
  | {
      type: 'chat'
      sessionId: string
      messageId: string
      senderType: SenderType
      senderId?: string
      content: string
      createdAt?: string
    }
  | { type: 'session_claimed'; sessionId: string; agentId: string }
  | {
      type: 'session_closed'
      sessionId: string
      closedBy: 'user' | 'agent'
      closerId?: string
      messageId: string
      senderType: 'system'
      content: string
      createdAt?: string
    }
  | { type: 'error'; message: string }
```

---

## 10. 前端实现要点

1. **先 REST 拉历史，再连 WS**，用 `messageId` 去重。  
2. 结束会话必须调 REST `close`；仅关闭浏览器/WS 不会把 `status` 改为 `closed`。  
3. 大厅请轮询 `lobby`，不要等 WS 推新单。  
4. 建议心跳 20～30s 发 `ping`。  
5. 断线重连：重新带 token 连同一地址；会话仍用原来的 `sessionId`。  
6. 本版不做：图片/文件、已读回执、输入中、转接、排队人数。

---

## 11. 联调检查清单

用户：

- [ ] POST sessions 得到 `active` 且无 `agentId`  
- [ ] 连 WS 收到 `connected` 且 `agent=false`  
- [ ] ping → pong  
- [ ] 发 chat，历史接口能查到  
- [ ] 客服接单后收到 `session_claimed` 和「客服已接入」  
- [ ] 结束会话只收到一条 `session_closed`，`closedBy=user`  
- [ ] 断开 WS 约 1 秒后对方看到「用户已离开会话」；重连看到「重新进入」

客服：

- [ ] `connected.agent === true`  
- [ ] lobby 能看到未接单  
- [ ] claim 后 mine 有该单，lobby 刷新后消失  
- [ ] 未 claim 发 chat 收到 error  
- [ ] 结束会话 `closedBy=agent`，文案为「客服已结束会话」

---

## 12. 接口速查

| 端 | 方法 | 路径 |
| --- | --- | --- |
| 用户 | POST | `/api/service/sessions` |
| 用户 | GET | `/api/service/sessions/current` |
| 用户 | GET | `/api/service/sessions/{id}` |
| 用户 | GET | `/api/service/sessions/{id}/messages` |
| 用户 | POST | `/api/service/sessions/{id}/close` |
| 客服 | GET | `/api/admin/service/sessions/lobby` |
| 客服 | GET | `/api/admin/service/sessions/mine` |
| 客服 | POST | `/api/admin/service/sessions/{id}/claim` |
| 客服 | GET | `/api/admin/service/sessions/{id}/messages` |
| 客服 | POST | `/api/admin/service/sessions/{id}/close` |
| 双方 | WS | `/ws/service?token={jwt}` |
