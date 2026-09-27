# Nacos 配置中心（阶段 12）说明

> 本目录是所有 Nacos 配置的**唯一事实来源**：`configs/` 下每个文件对应一个 dataId，
> `import-configs.py` 幂等地把它们推到 Nacos（同 dataId 覆盖）。

## 使用方式

```bash
# 1. 启动基础设施（Nacos 在其中）
docker compose -f docker/docker-compose.yml up -d nacos

# 2. 推送全部配置（幂等，可重复执行）
python docs/nacos/import-configs.py
# 需要 python 3；仅标准库。也可用 `python` 所在环境的任意 3.8+ 版本。

# 3. 重启业务服务（或等服务自己监听到变更）
```

## dataId 一览（group 均为 `HUASHUI_GROUP`）

| dataId | 内容 |
|---|---|
| `huashui-gateway-service.yml` | 网关主配置（暂为占位） |
| `huashui-user-service.yml` | 用户域主配置（暂为占位） |
| `huashui-product-service.yml` | `huashui.product.audit-required` 等业务参数 |
| `huashui-order-service.yml` | `huashui.order.pay-timeout-minutes` 等交易参数 |
| `huashui-gateway-flow-rules.json` | 网关限流规则（3 条 FlowRule） |
| `huashui-product-param-flow-rules.json` | 商品详情热点参数规则 |
| `huashui-order-degrade-rules.json` | 交易域熔断规则 |

## 规则改动入口

改规则 / 调参数都在 **Nacos 控制台**（http://127.0.0.1:18848/nacos）操作，秒级生效、无需重启；
Sentinel 控制台（18858）只用于看监控（实时曲线 / 簇点链路 / 熔断状态）——
官方控制台的「推规则」是推到客户端内存的，重启即丢，所以才把持久化交给 Nacos。

## ⚠️ configs/ 下的文件必须保持纯 ASCII

SCA 在把 Nacos 拉到的内容交给 YAML 解析器时使用 **JVM 默认编码**（中文 Windows 是 GBK），
内容里只要有中文注释，解析就会 `MalformedInputException` 失败，且**静默回退到代码默认值**——
表面上服务正常，实际 Nacos 配置根本没生效（本项目实测踩中）。
所以：中文说明写在本文件，`configs/` 里的推送内容只写英文注释。

这也与 `import-configs.py` 文件头注释里提到的「bash+curl 传中文被 GBK 转码」是同一类
Windows 编码问题的第三种表现（前两种见 `docs/sql` 的 `SET NAMES utf8mb4` 与项目记忆）。

## 与代码内置规则的关系（双保险）

代码里各 `SentinelRuleConfig` / `SentinelGatewayConfig` 仍会装载一份同默认值的规则：
- Nacos 正常时：数据源加载在后，**覆盖**内置值（Nacos 是事实来源）；
- Nacos 未启动时：数据源静默失败，**内置值兜底**，服务照常具备防护。
- 因此内置装载不能删，删了就失去降级能力。
