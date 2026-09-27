package cn.freedom.huashui.order.job;

import cn.freedom.huashui.common.enums.OrderStatus;
import cn.freedom.huashui.common.result.Result;
import cn.freedom.huashui.order.config.OrderProperties;
import cn.freedom.huashui.order.entity.Order;
import cn.freedom.huashui.order.mapper.OrderMapper;
import cn.freedom.huashui.order.mq.OrderMessageSender;
import cn.freedom.huashui.order.remote.ProductInvoker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「已锁定但没有有效订单」的商品兜底扫描。
 *
 * <p><b>它救的是什么：</b>
 * 下单的第一步是把商品置为「已锁定」。如果锁定成功、但订单因为任何原因没建出来
 * （进程崩溃、数据库抖动、跨服务调用超时），商品就会<b>永远停在已锁定</b>——
 * 没有任何别的机制会去解锁它：超时任务是按<b>订单</b>扫的，而这笔订单根本不存在。
 * 阶段 9 的验证里，这个状态真实出现过一次。
 *
 * <p><b>为什么扫描在订单域、候选名单来自商品域：</b>
 * 「商品是不是已锁定」只有商品库知道，「有没有有效订单」只有订单库知道，
 * 没有单个服务能同时看到两侧；而已冻结的依赖方向是 {@code order → product}
 * （反向禁止，避免循环依赖）。所以：商品域给出「锁定超时」的候选名单，
 * 订单域负责确认「确实没有有效订单」，再发出解锁消息。
 *
 * <p><b>为什么解锁走 MQ 而不是直接调 {@code /inner/product/{id}/unlock}：</b>
 * 走消息就复用了阶段 9 那套完整可靠性 —— 落本地消息表、事务提交后投递、
 * Confirm 确认、失败退避重试、重试耗尽进死信。直接调 Feign 的话，调用失败就没了，
 * 而商品会继续锁着，等于把「救援」本身变成了不可靠的一步。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleLockScanJob {

    /** 解锁原因，会写进本地消息记录与日志，供事后追溯「为什么这件商品被放回在售」 */
    private static final String UNLOCK_REASON_TEMPLATE = "商品锁定超过 %d 分钟且无有效订单，自动解锁";

    /**
     * 商品域调用的出口。不直接注入 {@code ProductClient}：直接调 Feign 会绕过熔断器，
     * 商品服务挂掉时每轮扫描都要干等满 Feign 超时（这正是兜底任务最不该发生的事）
     */
    private final ProductInvoker productInvoker;
    private final OrderMapper orderMapper;
    private final OrderMessageSender messageSender;
    private final OrderProperties orderProperties;

    @XxlJob("staleLockScanJob")
    public void scanStaleLockedProducts() {
        // 阈值 = 支付超时 + 缓冲。缓冲不能省：正常交易中的商品也是「已锁定」，
        // 卡在同一时刻会把正在付款的商品误判成异常、把商品从买家手里放走
        int beforeMinutes = orderProperties.getPayTimeoutMinutes()
                + orderProperties.getStaleLockBufferMinutes();

        List<Long> candidates;
        try {
            Result<List<Long>> response = productInvoker.listStaleLocked(
                    beforeMinutes, orderProperties.getStaleLockBatchSize());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String message = "商品服务返回异常，本轮跳过 | response=" + response;
                log.warn(message);
                XxlJobHelper.handleFail(message);
                return;
            }
            candidates = response.getData();
        } catch (Exception e) {
            // 商品服务不可用时绝不能「当成没有候选」：那会把一次故障伪装成一次正常巡检
            log.error("查询锁定超时的商品失败 | beforeMinutes={}", beforeMinutes, e);
            XxlJobHelper.handleFail("调用商品服务失败：" + e.getMessage());
            return;
        }

        if (candidates.isEmpty()) {
            XxlJobHelper.log("没有锁定超时的商品，本次跳过");
            return;
        }

        int unlocked = 0;
        int stillTrading = 0;
        for (Long productId : candidates) {
            // 有未取消的订单 → 说明交易正常推进中（哪怕订单已过期，也归订单超时任务管），跳过
            if (hasActiveOrder(productId)) {
                stillTrading++;
                continue;
            }
            messageSender.recordStaleLockUnlock(productId,
                    String.format(UNLOCK_REASON_TEMPLATE, beforeMinutes));
            unlocked++;
        }

        String summary = String.format(
                "锁定超时扫描完成 | 候选 %d 个、确认无订单并解锁 %d 个、交易进行中跳过 %d 个",
                candidates.size(), unlocked, stillTrading);
        XxlJobHelper.log(summary);
        log.info(summary);
    }

    /**
     * 是否存在「有效订单」。
     *
     * <p>只排除已取消（终态）：待支付、已付款、交易完成都算有效。
     * 待支付但已过期的订单不在这里处理 —— 它由 {@code orderTimeoutJob} 负责取消并解锁，
     * 两个任务各管一段，避免对同一笔交易做两次相反的动作。
     */
    private boolean hasActiveOrder(Long productId) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getProductId, productId)
                .ne(Order::getStatus, OrderStatus.CANCELLED.getCode()));
        return count != null && count > 0;
    }
}
