-- ============================================================
-- 05-xxl-job-init.sql  调度中心的执行器与任务定义（初始化数据）
--
-- 为什么做成脚本，而不是让人在控制台里手点：
--   1. 别人克隆仓库后导入即用，不需要照着文档点一遍；
--   2. 任务定义（cron、阻塞策略、handler 名）是可审查的文本，
--      错了能在 review 里看出来；点出来的配置没有痕迹；
--   3. 重建环境时不用回忆「上次是怎么配的」。
--
-- ⚠️ 幂等性说明：本脚本用 INSERT ... ON DUPLICATE KEY UPDATE（按主键 id）实现可重复执行。
--    为此显式指定了 id（1/2 为执行器组，1~4 为任务）。
--    xxl-job-admin 自身新增任务会用自增 id，只要 id ≥ 1000 就不会与本脚本冲突。
--
-- ⚠️ xxl_job_group.title 只有 varchar(12)，执行器名称必须写短名，
--    写成「华水闲置-订单服务（定时任务）」会直接报 Data too long
-- 🔴 必须给 glue_updatetime 赋值（本脚本已填 NOW()）：
--    xxl-job-admin 触发任务时会无条件读它（XxlJobTrigger 里的 setGlueUpdatetime(...getTime())），
--    留 NULL 时调度中心直接抛 NullPointerException，任务日志里只有 trigger_code=0 与一条空消息，
--    极难定位。官方 UI 新建任务会写这个字段，只有手写 SQL 才会漏。
-- 执行方式：mysql -uhuashui -p < 05-xxl-job-init.sql
-- ⚠️ 必须先执行 04-xxl-job.sql 建库建表
-- ============================================================

SET NAMES utf8mb4;
USE huashui_job;

-- ---------- 执行器组 ----------
-- address_type = 0 表示「自动注册」：执行器启动时会把自己的地址注册上来，
-- 因此这里不需要手写 IP。两个服务的 appname 必须与各自
-- application.yml 里的 xxl.job.executor.appname 完全一致，否则任务触发时找不到执行器。
INSERT INTO xxl_job_group (id, app_name, title, address_type, address_list, update_time)
VALUES (1, 'huashui-order-service-executor', '订单服务执行器', 0, NULL, NOW())
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name), title = VALUES(title),
                        address_type = VALUES(address_type), update_time = NOW();

INSERT INTO xxl_job_group (id, app_name, title, address_type, address_list, update_time)
VALUES (2, 'huashui-product-service-executor', '商品服务执行器', 0, NULL, NOW())
ON DUPLICATE KEY UPDATE app_name = VALUES(app_name), title = VALUES(title),
                        address_type = VALUES(address_type), update_time = NOW();

-- ---------- 任务定义 ----------
-- 共同配置的两个取值：
--   executor_block_strategy = DISCARD_LATER（丢弃后续调度）
--     上一轮还没跑完又来一次时，直接丢弃新的那一次。理由：这几个任务都是「把积压处理掉」，
--     重复触发没有意义；串行等待反而会让任务排队堆积，掩盖真正的耗时问题。
--   executor_fail_retry_count = 0（失败不自动重试）
--     失败原因多半是下游不可用或代码缺陷，立即重试只会把日志刷得更乱；
--     下一个调度周期自然会再来一次，那才是有效的重试。
--   misfire_strategy = DO_NOTHING（调度过期不补跑）
--     服务停机期间错过的调度不需要补：这些任务处理的是「当前状态」而不是「某个时刻的事件」，
--     补跑历史调度只会重复劳动（例如停机 1 小时后又补跑 60 次超时扫描）。

-- ① 超时未付款订单清理（每分钟）
INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy,
     executor_route_strategy, executor_handler, executor_param, executor_block_strategy,
     executor_timeout, executor_fail_retry_count, glue_type, glue_source, glue_remark,
     glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (1, 1, '超时未付款订单清理：取消已过支付截止时间的订单，并经本地消息表 + MQ 通知商品域恢复在售',
     NOW(), NOW(), 'huashui', '',
     'CRON', '0 0/1 * * * ?', 'DO_NOTHING',
     'FIRST', 'orderTimeoutJob', '', 'DISCARD_LATER',
     0, 0, 'BEAN', NULL, '脚本初始化', NOW(), NULL, 1, 0, 0)
ON DUPLICATE KEY UPDATE job_group = VALUES(job_group), job_desc = VALUES(job_desc),
                        schedule_conf = VALUES(schedule_conf), executor_handler = VALUES(executor_handler),
                        executor_block_strategy = VALUES(executor_block_strategy),
                        trigger_status = VALUES(trigger_status),
                        glue_remark = VALUES(glue_remark), glue_updatetime = VALUES(glue_updatetime),
                        update_time = NOW();

-- ② 锁定超时且无有效订单的商品解锁（每 10 分钟）
INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy,
     executor_route_strategy, executor_handler, executor_param, executor_block_strategy,
     executor_timeout, executor_fail_retry_count, glue_type, glue_source, glue_remark,
     glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (2, 1, '锁定超时扫描：找出「已锁定但长时间无有效订单」的商品并发出解锁消息（兜底阶段 7 的脏状态）',
     NOW(), NOW(), 'huashui', '',
     'CRON', '0 0/10 * * * ?', 'DO_NOTHING',
     'FIRST', 'staleLockScanJob', '', 'DISCARD_LATER',
     0, 0, 'BEAN', NULL, '脚本初始化', NOW(), NULL, 1, 0, 0)
ON DUPLICATE KEY UPDATE job_group = VALUES(job_group), job_desc = VALUES(job_desc),
                        schedule_conf = VALUES(schedule_conf), executor_handler = VALUES(executor_handler),
                        executor_block_strategy = VALUES(executor_block_strategy),
                        trigger_status = VALUES(trigger_status),
                        glue_remark = VALUES(glue_remark), glue_updatetime = VALUES(glue_updatetime),
                        update_time = NOW();

-- ③ 本地消息补发（每 30 秒）
INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy,
     executor_route_strategy, executor_handler, executor_param, executor_block_strategy,
     executor_timeout, executor_fail_retry_count, glue_type, glue_source, glue_remark,
     glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (3, 1, '本地消息补发：把「已落库但尚未成功投递」的消息再发一次（跨服务事务降级成「本地事务 + 异步重试」的那一环）',
     NOW(), NOW(), 'huashui', '',
     'CRON', '0/30 * * * * ?', 'DO_NOTHING',
     'FIRST', 'localMessageRetryJob', '', 'DISCARD_LATER',
     0, 0, 'BEAN', NULL, '脚本初始化', NOW(), NULL, 1, 0, 0)
ON DUPLICATE KEY UPDATE job_group = VALUES(job_group), job_desc = VALUES(job_desc),
                        schedule_conf = VALUES(schedule_conf), executor_handler = VALUES(executor_handler),
                        executor_block_strategy = VALUES(executor_block_strategy),
                        trigger_status = VALUES(trigger_status),
                        glue_remark = VALUES(glue_remark), glue_updatetime = VALUES(glue_updatetime),
                        update_time = NOW();

-- ④ 浏览量结算（每 5 分钟）
INSERT INTO xxl_job_info
    (id, job_group, job_desc, add_time, update_time, author, alarm_email,
     schedule_type, schedule_conf, misfire_strategy,
     executor_route_strategy, executor_handler, executor_param, executor_block_strategy,
     executor_timeout, executor_fail_retry_count, glue_type, glue_source, glue_remark,
     glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time)
VALUES
    (4, 2, '浏览量结算：把 Redis 中累加的浏览量按差值批量回写数据库（避免每次浏览都对同一行加锁）',
     NOW(), NOW(), 'huashui', '',
     'CRON', '0 0/5 * * * ?', 'DO_NOTHING',
     'FIRST', 'viewCountFlushJob', '', 'DISCARD_LATER',
     0, 0, 'BEAN', NULL, '脚本初始化', NOW(), NULL, 1, 0, 0)
ON DUPLICATE KEY UPDATE job_group = VALUES(job_group), job_desc = VALUES(job_desc),
                        schedule_conf = VALUES(schedule_conf), executor_handler = VALUES(executor_handler),
                        executor_block_strategy = VALUES(executor_block_strategy),
                        trigger_status = VALUES(trigger_status),
                        glue_remark = VALUES(glue_remark), glue_updatetime = VALUES(glue_updatetime),
                        update_time = NOW();

-- ---------- 执行结果（自检） ----------
SELECT g.id AS 组id, g.app_name AS 执行器, i.id AS 任务id, i.executor_handler AS handler,
       i.schedule_conf AS cron, i.trigger_status AS 启用
FROM xxl_job_group g LEFT JOIN xxl_job_info i ON i.job_group = g.id
ORDER BY g.id, i.id;
