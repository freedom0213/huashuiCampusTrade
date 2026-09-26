-- ============================================================
-- ⚠️⚠️ 危险脚本：删除本项目的全部业务表 ⚠️⚠️
--
-- 用途：开发期表结构变更后重建。执行顺序为
--         99-reset-tables.sql → 01-user.sql → 02-product.sql → 03-order.sql
--
-- 警告：
--   1. 本脚本会删除数据，且【不可恢复】——MySQL 的 DROP 不经过操作系统回收站
--   2. 所有表名均【显式写出】，不使用任何通配符，
--      确保不会误删本机其他项目（tlias / sky_take_out / mp 等）的表
--   3. 生产环境绝不允许执行此类脚本
-- ============================================================

SET NAMES utf8mb4;

-- ---------------- huashui_user ----------------
USE huashui_user;
DROP TABLE IF EXISTS t_user;

-- ---------------- huashui_product ----------------
USE huashui_product;
DROP TABLE IF EXISTS t_category;
DROP TABLE IF EXISTS t_product;
DROP TABLE IF EXISTS t_product_image;
DROP TABLE IF EXISTS t_favorite;

-- ---------------- huashui_order ----------------
USE huashui_order;
DROP TABLE IF EXISTS t_orders;
DROP TABLE IF EXISTS t_local_message;
