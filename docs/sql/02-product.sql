-- ============================================================
-- 商品域建表脚本  ——  database: huashui_product
-- ============================================================

-- 【必须保留】强制声明连接字符集为 utf8mb4，原因见 01-user.sql 中的说明。
SET NAMES utf8mb4;

USE huashui_product;

-- ------------------------------------------------------------
-- 商品分类
-- 做成表而不是枚举的原因：分类会随运营调整（新增「乐器」「自行车」等），
-- 写死成枚举每次都要改代码 + 发版。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_category
(
    id          BIGINT      NOT NULL COMMENT '主键',
    name        VARCHAR(32) NOT NULL COMMENT '分类名称',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商品分类表';

-- ------------------------------------------------------------
-- 商品表
-- 状态机：0 待审核 → 1 在售 → 2 已锁定 → 3 已售出（终态）
--         另有两个分支：4 已下架（卖家主动）、5 已驳回（审核不通过）
-- 并发下单一律用条件更新：
--   UPDATE t_product SET status = 2 WHERE id = ? AND status = 1
--   判断受影响行数，靠 InnoDB 行锁保证原子性，不需要分布式锁
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product
(
    id              BIGINT         NOT NULL COMMENT '主键（雪花 ID）',
    seller_id       BIGINT         NOT NULL COMMENT '卖家用户 id',
    category_id     BIGINT         NOT NULL COMMENT '分类 id',
    campus          VARCHAR(16)    NOT NULL COMMENT '校区：龙子湖 / 花园 / 江淮。江淮校区在信阳，与郑州两校区不通勤，故单列存储以便筛选',
    title           VARCHAR(64)    NOT NULL COMMENT '商品标题',
    description     VARCHAR(1000)  NOT NULL DEFAULT '' COMMENT '商品描述',
    condition_level TINYINT        NOT NULL DEFAULT 1 COMMENT '成色：0 全新，1 九成新，2 七成新，3 五成新及以下',
    price           DECIMAL(10, 2) NOT NULL COMMENT '售价',
    original_price  DECIMAL(10, 2)          DEFAULT NULL COMMENT '原价，可为空（用于展示划线价）',
    cover_url       VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '封面图地址',
    trade_place     VARCHAR(128)   NOT NULL COMMENT '交易地点，如「龙子湖 · 第二食堂」。本项目只支持校内当面自提，不做配送',
    view_count      INT            NOT NULL DEFAULT 0 COMMENT '浏览量。由 Redis 累加、XXL-JOB 定时结算回写，避免行锁热点',
    favorite_count  INT            NOT NULL DEFAULT 0 COMMENT '收藏数',
    status          TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0 待审核，1 在售，2 已锁定，3 已售出，4 已下架，5 已驳回',
    reject_reason   VARCHAR(255)   NOT NULL DEFAULT '' COMMENT '审核驳回原因',
    publish_time    DATETIME                DEFAULT NULL COMMENT '上架时间（审核通过时写入）',
    sold_time       DATETIME                DEFAULT NULL COMMENT '售出时间',
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    -- 「我发布的商品」列表 + 「我的发布」按状态筛选
    KEY idx_seller_id_status (seller_id, status),
    -- 首页按分类浏览：分类 + 状态
    KEY idx_category_id_status (category_id, status),
    -- 按校区筛选在售商品：校区 + 状态 + 上架时间
    -- 三列组合同时覆盖「筛选」与「按时间倒序」，避免回表后再 filesort
    KEY idx_campus_status_time (campus, status, publish_time),
    -- 首页默认排序：在售商品按上架时间倒序，该组合索引可直接支撑排序
    KEY idx_status_publish_time (status, publish_time),
    -- 关键词搜索。注意：LIKE '%关键词%' 用不到该索引，这里只是为「前缀匹配」和将来接 ES 留位置
    KEY idx_title (title)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商品表';

-- ------------------------------------------------------------
-- 商品图片（一个商品多张图，与商品表一对多拆开，避免把图片塞进商品行）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_product_image
(
    id          BIGINT       NOT NULL COMMENT '主键',
    product_id  BIGINT       NOT NULL COMMENT '商品 id',
    url         VARCHAR(255) NOT NULL COMMENT '图片地址',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序，越小越靠前',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_product_id (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商品图片表';

-- ------------------------------------------------------------
-- 收藏
-- 归属 product-service 而不是 user-service：
--   1. 语义上「收藏」是商品的一个被关注属性
--   2. 表和商品同库，查「我的收藏」可以直接关联商品，不需要跨服务调用
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_favorite
(
    id          BIGINT   NOT NULL COMMENT '主键',
    user_id     BIGINT   NOT NULL COMMENT '用户 id',
    product_id  BIGINT   NOT NULL COMMENT '商品 id',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    -- 唯一索引从数据库层面兜住「重复收藏」，不依赖应用层先查后插（并发下不可靠）
    UNIQUE KEY uk_user_product (user_id, product_id),
    -- 「我的收藏」按时间倒序分页
    KEY idx_user_id_create_time (user_id, create_time)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='商品收藏表';

-- ------------------------------------------------------------
-- 分类初始数据
-- 用固定小 id 便于开发期引用；INSERT IGNORE 保证脚本可重复执行
-- ------------------------------------------------------------
INSERT IGNORE INTO t_category (id, name, sort, status)
VALUES (1, '教材书籍', 10, 1),
       (2, '数码电子', 20, 1),
       (3, '生活用品', 30, 1),
       (4, '运动器材', 40, 1),
       (5, '服饰鞋包', 50, 1),
       (6, '其他', 99, 1);
