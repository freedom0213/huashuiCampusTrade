-- ============================================================
-- 用户域建表脚本  ——  database: huashui_user
--
-- 通用约定：
--   1. 主键统一为大整数（应用侧用雪花 ID 生成，因此不设 AUTO_INCREMENT）
--   2. 时间统一 DATETIME + 东八区，应用侧用 LocalDateTime
--   3. 所有删除走逻辑删除（deleted 字段），不做物理删除
--   4. 脚本使用 CREATE TABLE IF NOT EXISTS，可重复执行，不会清空已有数据
-- ============================================================

USE huashui_user;

-- ------------------------------------------------------------
-- 用户表
-- 说明：本项目不区分买家 / 卖家 —— 校园二手的本质是同一个人兼具两种身份，
--       拆成两张表会立刻产生「身份如何合并」的问题，因此用一张表 + role 字段表达。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_user
(
    id          BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名，登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 哈希，不可逆）。长度 100 是为将来换算法留余量',
    nickname    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '昵称，展示用',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号，仅做唯一性校验，不发验证码',
    avatar      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '头像地址',
    student_no  VARCHAR(32)           DEFAULT NULL COMMENT '学号（可选）。未填写请存 NULL，不要存空字符串，否则会互相冲突唯一索引',
    dept        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '院系',
    role        TINYINT      NOT NULL DEFAULT 1 COMMENT '角色：1 学生，2 管理员',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1 正常，0 禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    UNIQUE KEY uk_student_no (student_no),
    KEY idx_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci COMMENT ='用户表';

-- 注意：由于采用逻辑删除，被删除用户的 username / phone 仍会占用唯一索引。
-- 如需支持「注销后重新注册同名账号」，应在删除时给 username 追加随机后缀。
-- 本项目不做该处理。
