package cn.freedom.huashui.order.service;

import cn.freedom.huashui.order.config.OrderProperties;
import cn.freedom.huashui.order.entity.LocalMessage;
import cn.freedom.huashui.order.mapper.LocalMessageMapper;
import cn.freedom.huashui.order.mq.LocalMessageStatus;
import cn.freedom.huashui.order.mq.OrderMessageSender;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息补发 —— 「消息一定会发出去」这句话的兑现者。
 *
 * <p>扫描「待发送且已到重试时间」的记录重新投递。正常路径走不到这里
 * （事务提交后那次投递通常就成功了），它兜的是发送失败、Broker nack、
 * 以及进程在提交后立刻崩溃这些情况。
 *
 * <p><b>为什么抽成一个 Service，而不是直接写在定时任务里：</b>
 * 触发它的入口有两个 —— 阶段 9 的本地 {@code @Scheduled} 任务（保留作降级开关）
 * 与阶段 10 的 XXL-JOB 任务。逻辑留在一处，两个入口各自只负责「什么时候调」，
 * 避免两份实现随字段增减慢慢跑偏（同一项目里已吃过这个亏，见 {@code ProductConverter}）。
 *
 * <p><b>多实例重复执行安全</b>：两个实例同时捞到同一条记录时，
 * 投递两次的结果是消费端收到两条相同 msgId 的消息 —— 消费端有幂等去重兜着，
 * 而状态更新走的是带前置条件的 UPDATE，不会互相覆盖。
 *
 * @author freedom0213
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageRetryService {

    private final LocalMessageMapper localMessageMapper;
    private final OrderMessageSender messageSender;
    private final OrderProperties orderProperties;

    /**
     * @return 本次捞到并尝试重投的条数（0 表示没有待补发的消息）
     */
    public int retryPendingMessages() {
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
            return 0;
        }
        log.info("本地消息补发：捞到 {} 条待补发记录", pending.size());
        for (LocalMessage record : pending) {
            // 单条失败不能中断整批：publish 内部已自行捕获并安排下一次重试
            messageSender.publish(record);
        }
        return pending.size();
    }
}
