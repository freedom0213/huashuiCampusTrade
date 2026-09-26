-- ============================================================
-- 交易域建表脚本  ——  database: huashui_order
-- ============================================================

-- 【必须保留】强制声明连接字符集为 utf8mb4，原因见 01-user.sql 中的说明。
SET NAMES utf8mb4;

USE huashui_order;

-- ------------------------------------------------------------
-- 订单表
--
-- 状态机：0 待支付 → 1 已付款 → 2 交易完成（终态）
--         分支   0 待支付 → 3 已取消（终态）→ MQ 通知商品域恢复「在售」
--
-- 【为什么冗余商品标题 / 封面 / 价格 / 交易地点】
--   1. 业务刚需：成交价必须快照。卖家事后改价不能影响历史订单。
--   2. 顺带解决跨库查询：order 库不需要 JOIN product 库的表。
--   3. trade_place 是面交场景的刚需：订单详情页要告诉买家「去哪见面」，
--      若靠 productId 反查商品，商品被逻辑删除后就查不到了，所以一并快照。
--   规则：订单一旦创建，快照不再与商品同步。
--
-- 【为什么没有 deleted 字段】
--   订单是交易凭证，属于不允许删除的业务数据，因此不做逻辑删除。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_orders
(
    id            BIGINT         NOT NULL COMMENT '主键（雪花 ID）',
    order_no      VARCHAR(32)    NOT NULL COMMENT '订单号：时间戳 + 随机数，不暴露真实单量',
    buyer_id      BIGINT         NOT NULL COMMENT '买家用户 id',
    seller_id     BIGINT         NOT NULL COMMENT '卖家用户 id',
    product_id    BIGINT         NOT NULL COMMENT '商品 id',
    product_title VARCHAR(64)    NOT NULL COMMENT '商品标题快照',
    product_cover VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '商品封面快照',
    product_price DECIMAL(10, 2) NOT NULL COMMENT '商品单价快照（成交价）',
    trade_place   VARCHAR(128)   NOT NULL DEFAULT '' COMMENT '交易地点快照（面交场景买家据此赴约），创建后不再同步',
    total_amount  DECIMAL(10, 2) NOT NULL COMMENT '订单总额。当前一单一商品，等于单价；保留该字段以便将来支持多商品',
    status        TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0 待支付，1 已付款，2 交易完成，3 已取消',
    close_deadline DATETIME      NOT NULL COMMENT '支付截止时间（创建时间 + 配置的超时分钟数）。XXL-JOB 据此字段扫描超时订单',
    pay_time      DATETIME                DEFAULT NULL COMMENT '付款时间',
    finish_time   DATETIME                DEFAULT NULL COMMENT '完成时间',
    cancel_time   DATETIME                DEFAULT NULL COMMENT '取消时间',
    cancel_reason VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '取消原因',
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    -- 「我买到的」按状态筛选
    KEY idx_buyer_id_status (buyer_id, status),
    -- 「我卖出的」按状态筛选
    KEY idx_seller_id_status (seller_id, status),
    -- 关键索引：订单超时扫描任务
    --   SELECT ... WHERE status = 0 AND close_deadline < NOW() LIMIT 200
    -- 该组合索引让扫描直接命中，避免全表扫
    KEY idx_status_close_deadline (status, close_deadline)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='订单表';

-- ------------------------------------------------------------
-- 本地消息表（80% 阶段启用）
--
-- 解决的问题：跨服务无法用本地事务保证「订单已取消」和「消息已发出」同时成立。
--   若先改订单再发消息，发消息失败 → 商品永远锁死；
--   若先发消息再改订单，改订单失败 → 商品被错误释放。
-- 做法：把「发消息」这件事也当成一条本地数据，与业务变更放在同一个本地事务里落库，
--       再由定时任务扫描 status = 0 的记录投递到 MQ，投递成功才改状态。
--       这样就把「跨服务事务」降级成了「本地事务 + 异步重试」。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_local_message
(
    id             BIGINT       NOT NULL COMMENT '主键',
    msg_id         VARCHAR(64)  NOT NULL COMMENT '消息唯一 id，消费端据此做幂等',
    exchange       VARCHAR(64)  NOT NULL COMMENT '目标交换机',
    routing_key    VARCHAR(64)  NOT NULL COMMENT '路由键',
    biz_type       VARCHAR(32)  NOT NULL COMMENT '业务类型，如 ORDER_CANCELED',
    biz_id         VARCHAR(64)  NOT NULL COMMENT '业务主键，如 order_no',
    payload        TEXT         NOT NULL COMMENT '消息体 JSON',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0 待发送，1 已发送，2 已确认，3 失败待人工处理',
    retry_count    INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_time DATETIME             DEFAULT NULL COMMENT '下次重试时间（退避重试）',
    error_msg      VARCHAR(500) NOT NULL DEFAULT '' COMMENT '最后一次失败原因',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_id (msg_id),
    -- 定时任务扫描待发送 / 待重试的消息
    KEY idx_status_next_retry_time (status, next_retry_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='本地消息表（保证消息最终一定发出）';
