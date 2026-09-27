-- ============================================================
-- 管理员账号初始化  ——  database: huashui_user
--
-- 阶段 13（商品审核 + 管理端）配套：
--   账号 admin01 / admin123456（role = 2 管理员）
--   密码为 BCrypt 哈希，明文仅本机演示用，不要用于任何真实环境
--
-- 幂等：NOT EXISTS 保证重复执行不会插入第二条 / 撞唯一索引
-- ============================================================

-- 【必须保留】防止 UTF-8 脚本被本机 mysql 客户端按 GBK 误读成乱码
SET NAMES utf8mb4;

USE huashui_user;

INSERT INTO t_user (id, username, password, nickname, phone, role, status)
SELECT 10001,
       'admin01',
       '$2b$10$J9dea/yVDatGH2U2k8EeQOBTqk8AeHhvImBXYZLqTPUetBAjmqwNy',
       '平台管理员',
       '19900000001',
       2,
       1
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username = 'admin01' AND deleted = 0);
