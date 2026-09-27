package cn.freedom.huashui.order.job;

import cn.freedom.huashui.order.service.LocalMessageRetryService;
import cn.freedom.huashui.order.service.OrderService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单域的两个计划任务（XXL-JOB 版本）。
 *
 * <p>两个任务都由 {@code @XxlJob} 注解暴露给调度中心，方法名即调度中心里配置的
 * 「JobHandler」。阶段 9 之前它们跑在本地的 {@code @Scheduled} 里，
 * 迁到 XXL-JOB 之后有三个变化：<b>可观测</b>（调度中心能看到每次执行的结果与耗时）、
 * <b>可手动触发</b>（排查时不用等下一个周期）、<b>可控</b>（改 cron 不用改代码重启）。
 *
 * <p><b>为什么每个方法都要自己 try-catch：</b>
 * 任务一旦抛出异常，本次执行会记为失败（这是对的，调度中心会报警），
 * 但异常本身会让「这一轮什么也没做」。所以这里兜住异常、记录上下文，
 * 再用 {@code XxlJobHelper.handleFail} 明确告诉调度中心这次失败了 ——
 * 既不让任务静默停摆，也不把失败伪装成成功。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduledJob {

    private final OrderService orderService;
    private final LocalMessageRetryService localMessageRetryService;

    /**
     * 超时未付款订单的兜底清理：查询「待支付且已过支付截止时间」的订单并取消。
     *
     * <p>取消之后由本地消息表 + MQ 通知商品域恢复「在售」（阶段 9 的链路）。
     */
    @XxlJob("orderTimeoutJob")
    public void closeExpiredOrders() {
        try {
            int cancelled = orderService.closeExpiredOrders();
            String summary = "超时订单清理完成 | 本轮取消 " + cancelled + " 笔";
            XxlJobHelper.log(summary);
            log.info(summary);
        } catch (Exception e) {
            log.error("超时订单清理任务执行异常", e);
            XxlJobHelper.handleFail("超时订单清理异常：" + e.getMessage());
        }
    }

    /**
     * 本地消息补发：把「已落库但尚未成功投递」的消息再发一次。
     *
     * <p>这个任务是把跨服务事务降级成「本地事务 + 异步重试」的那一环。
     */
    @XxlJob("localMessageRetryJob")
    public void retryPendingMessages() {
        try {
            int count = localMessageRetryService.retryPendingMessages();
            XxlJobHelper.log("本地消息补发完成 | 本轮处理 " + count + " 条");
        } catch (Exception e) {
            log.error("本地消息补发任务执行异常", e);
            XxlJobHelper.handleFail("本地消息补发异常：" + e.getMessage());
        }
    }
}
