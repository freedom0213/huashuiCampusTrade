# huashui-web · 用户端移动 H5

华水校园闲置交易平台的用户端前端。**Vue 3 + Vite**，375 宽移动端优先，桌面上限宽 480px 居中。

> 管理员端本轮不实现。

## 运行

```bash
npm install          # 首次
npm run dev          # http://127.0.0.1:5173
```

**开发环境只连网关 6001**（`vite.config.js` 里配了 `/api` 与 `/uploads` 两条代理），
不直连 6002 / 6003 / 6004。所以开发前需要先把后端基础设施和 4 个服务跑起来。

## 目录约定

```
src/
├── api/            请求层。request.js 是统一封装，其余按服务分文件（user/product/order/favorite/category/file）
├── components/     通用组件（TabBar 底栏、AppToast 全局反馈提示）
├── composables/    组合式函数（useToast）
├── constants/      枚举与常量（状态码、成色、校区、错误码、排序）—— 与后端枚举一一对应
├── router/         路由表。每条路由的 meta.apis 列出「本页要调用哪些接口」
├── stores/         Pinia（user 登录态）
├── styles/         variables.css 设计令牌 + base.css 基础样式与组件基元
├── utils/          格式化工具（价格、时间、倒计时）
└── views/          页面
```

## 三条不可违反的规则

1. **不写 mock 数据。** 一律用真实接口联调。页面跑不通报的是接口问题，不是"先塞假数据糊过去"。
2. **接口以 `docs/03-前端接口核对清单.md` 为准。** 那是逐个读后端 Controller/DTO/VO 实测产出的，
   比任何口头描述可靠。改接口先改那份清单。
3. **视觉以 `docs/design/ui-v1.html` 为准**，实现约束见 `docs/02-前端设计V1.md`。
   - 主色玫瑰粉 `#EC6E9C`；**整屏粉色不超过 4 处**
   - 头像一律「渐变圆 + 白色人像图标」，禁止用姓氏首字
   - 反馈提示**只有 `✓` 与 `！`**，没有 `✗`（`！` 必须写出失败原因）

## 两条容易踩的坑

- **所有 `Long` 已被后端序列化成字符串**（雪花 ID 精度）。所有 id 按 `string` 处理，**不要 `parseInt`**。
  数量类字段（`viewCount` / `remainSeconds` / `total` 等）是数字。
- **网关 `allow-credentials=false`** → axios 不要开 `withCredentials`，token 走 `Authorization` 请求头。

## 分块推进计划

| 块 | 内容 | 状态 |
|---|---|---|
| 1 | 工程骨架 + 设计令牌 + 请求层 + 路由 + 底栏 + 反馈组件 | ✅ |
| 2 | 账号线：登录 / 注册 / 我的 / 个人资料 | 待开始 |
| 3 | 浏览线：首页 / 搜索 / 详情 / 卖家主页 | 待开始 |
| 4 | 发布线：发布商品 / 我的发布 | 待开始 |
| 5 | 交易线：下单 / 我的订单 / 订单详情 | 待开始 |
| 6 | 收藏 + 通知中心 | 待开始 |

每个块完成 → 一次 commit → push。
