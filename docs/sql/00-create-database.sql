-- ============================================================
-- 华水校园闲置交易平台 —— 建库脚本
-- 说明：同一 MySQL 实例上建立 3 个独立 database，每个服务只连自己的库。
--       这样做的目的是守住服务边界，避免跨服务随意 JOIN。
-- 执行方式：mysql -uroot -p < 00-create-database.sql
-- ============================================================

-- 用户域
CREATE DATABASE IF NOT EXISTS huashui_user
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 商品域
CREATE DATABASE IF NOT EXISTS huashui_product
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- 交易域
CREATE DATABASE IF NOT EXISTS huashui_order
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

-- ------------------------------------------------------------
-- 应用账号（敏感信息，真实密码请放到本地 application-local.yml，不要提交到仓库）
-- ------------------------------------------------------------
-- CREATE USER IF NOT EXISTS 'huashui'@'localhost' IDENTIFIED BY '<你的密码>';
-- GRANT ALL PRIVILEGES ON huashui_user.*    TO 'huashui'@'localhost';
-- GRANT ALL PRIVILEGES ON huashui_product.* TO 'huashui'@'localhost';
-- GRANT ALL PRIVILEGES ON huashui_order.*   TO 'huashui'@'localhost';
-- FLUSH PRIVILEGES;
