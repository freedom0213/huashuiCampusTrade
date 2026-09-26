package cn.freedom.huashui.order.task;

import cn.freedom.huashui.order.config.OrderProperties;
import cn.freedom.huashui.order.entity.LocalMessage;
import cn.freedom.huashui.order.mapper.LocalMessageMapper;
import cn.freedom.huashui.order.mq.LocalMessageStatus;
import cn.freedom.huashui.order.mq.OrderMessageSender;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息补发任务 —— 「消息一定会发出去」这句话的兑现者。
 *
 * <p>它扫描「待发送且已到重试时间」的记录重新投递。正常的路径走不到这里
 * （事务提交后那次投递通常就成功了），它是留给发送失败、Broker nack、
 * 以及进程在提交后立刻崩溃这些情况的兜底。
 *
 * <p><b>为什么用本地 {@code @Scheduled} 而不是一上来就 XXL-JOB：</b>
 * 分布式任务调度是本项目阶段 10 的内容。但「消息发不出去」是个功能性缺陷，
 * 不能等到那时才补，所以先用本地任务顶上；阶段 10 接入后把
 * {@code huashui.order.local-message-task-enabled} 改成 {@code false} 即可停用它。
 *
 * <p><b>多实例重复执行安全</b>：两个实例同时捞到同一条记录时，
 * 投递两次的结果是消费端拿到两条相同 msgId 的消息 —— 消费端有幂等去重兜着，
 * 而状态的更新走的是带前置条件的 UPDATE，不会互相覆盖。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "huashui.order",
        name = "local-message-task-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LocalMessageRetryTask {

    private final LocalMessageMapper localMessageMapper;
    private final OrderMessageSender messageSender;
    private final OrderProperties orderProperties;

    @Scheduled(fixedDelayString = "${huashui.order.local-message-task-interval-ms:30000}")
    public void retryPendingMessages() {
        try {
            // 用分页插件限制条数，而不是 last("LIMIT n")：后者是字符串拼 SQL，
            // 今天拼的是 int 很安全，但会立一个坏先例
            Page<LocalMessage> page = new Page<>(1, orderProperties.getLocalMessageBatchSize());
            page.setSearchCount(false);   // 不需要总数，省一次 count 查询

            List<LocalMessage> pending = localMessageMapper.selectPage(page,
                    new LambdaQueryWrapper<LocalMessage>()
                            .eq(LocalMessage::getStatus, LocalMessageStatus.PENDING)
                            .le(LocalMessage::getNextRetryTime, LocalDateTime.now())
                            .orderByAsc(LocalMessage::getNextRetryTime)).getRecords();

            if (pending.isEmpty()) {
                return;
            }
            log.info("本地消息补发任务：捞到 {} 条待补发记录", pending.size());
            for (LocalMessage record : pending) {
                // 单条失败不能中断整批：publish 内部已自行捕获并安排下一次重试
                messageSender.publish(record);
            }
        } catch (Exception e) {
            // 定时任务里抛出的异常会终止本次调度，而它一旦静默停摆，
            // 「消息永远发不出去」就会变成没人知道的事，所以必须自己兜住
            log.error("本地消息补发任务执行异常", e);
        }
    }
}
