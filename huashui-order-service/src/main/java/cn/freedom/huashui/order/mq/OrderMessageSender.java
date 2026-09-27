package cn.freedom.huashui.order.mq;

import cn.freedom.huashui.common.api.mq.OrderCancelledMessage;
import cn.freedom.huashui.common.constant.MqConstants;
import cn.freedom.huashui.order.config.OrderProperties;
import cn.freedom.huashui.order.entity.LocalMessage;
import cn.freedom.huashui.order.entity.Order;
import cn.freedom.huashui.order.mapper.LocalMessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单消息发送器 —— 本阶段可靠性的核心。
 *
 * <p><b>它解决的问题（不处理会怎样）：</b>
 * 「订单取消」和「消息发出」是两个动作。若先改订单再发消息，发消息时进程崩溃 →
 * 订单已取消，但商品<b>永远停在已锁定</b>——没有任何机制会去解锁它
 * （超时任务扫的是订单表，而这笔订单已经不是待支付了）。
 *
 * <p><b>做法：把「要发消息」也当成一条本地数据。</b>
 * 订单状态变更与消息记录写在<b>同一个本地事务</b>里，于是「订单取消了」与
 * 「消息被记下来了」必然同时成立。消息真正投出去是之后的事——
 * 投失败有补发任务兜底，补发再失败还有人工介入。
 * 这就是把跨服务的分布式事务降级成「<b>本地事务 + 异步重试</b>」，
 * 也是本项目不引入 Seata 的底气。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageSender {

    /** 业务类型标识，写进本地消息表供人工排查 */
    public static final String BIZ_TYPE_ORDER_CANCELED = "ORDER_CANCELED";

    /** 业务类型：兜底扫描发现「锁定超时且无有效订单」，通知商品域解锁 */
    public static final String BIZ_TYPE_STALE_LOCK_UNLOCK = "STALE_LOCK_UNLOCK";

    /** 与 {@code t_local_message.error_msg} 的 {@code VARCHAR(500)} 对齐 */
    private static final int ERROR_MSG_MAX_LENGTH = 500;

    /**
     * 补发的退避策略（秒）：1min → 2min → 5min → 10min → 30min。
     *
     * <p>比消费侧的重试间隔（1s/2s/4s）长得多，因为失败的成因不同：
     * 消费侧失败多半是「数据库瞬时抖动」，秒级重试就能恢复；
     * 这里失败通常是「RabbitMQ 不可用」，属于更严重的故障，
     * 密集重试不但没用，还会把日志刷得没法看。
     */
    private static final long[] RETRY_BACKOFF_SECONDS = {60, 120, 300, 600, 1800};

    private final LocalMessageMapper localMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderProperties orderProperties;

    /**
     * ① 把「订单已取消」消息记入本地消息表。
     *
     * <p><b>{@code Propagation.MANDATORY} 是刻意的</b>：它要求调用方必须已经开启事务，
     * 否则直接抛异常。这样「消息记录必须与业务变更同事务」这条约束是<b>由框架强制</b>的，
     * 而不是靠一句注释提醒后来者 —— 万一有人把它挪到事务外调用，
     * 会在启动后的第一次取消订单时立刻炸出来，而不是悄悄留下一个不一致的空窗期。
     *
     * <p>返回后消息已经在库里了；真正的投递发生在事务提交之后（见方法末尾注册的回调）。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOrderCancelled(Order order, String reason) {
        OrderCancelledMessage message = new OrderCancelledMessage();
        message.setMsgId(UUID.randomUUID().toString().replace("-", ""));
        message.setOrderNo(order.getOrderNo());
        message.setProductId(order.getProductId());
        message.setBuyerId(order.getBuyerId());
        message.setSellerId(order.getSellerId());
        message.setCancelTime(LocalDateTime.now());

        LocalMessage record = new LocalMessage();
        record.setMsgId(message.getMsgId());
        record.setExchange(MqConstants.TRADE_EXCHANGE);
        record.setRoutingKey(MqConstants.ORDER_CANCEL_ROUTING_KEY);
        record.setBizType(BIZ_TYPE_ORDER_CANCELED);
        record.setBizId(order.getOrderNo());
        // 保存消息原文而不是引用订单表：订单后续若被改动，消息内容不该跟着变
        record.setPayload(serialize(message));
        record.setStatus(LocalMessageStatus.PENDING);
        record.setRetryCount(0);
        // 设为「现在」= 允许补发任务立刻捞起来。正常路径走不到补发，
        // 它是「提交后那次投递没成功」的兜底
        record.setNextRetryTime(LocalDateTime.now());
        record.setErrorMsg("");

        localMessageMapper.insert(record);
        log.info("订单取消消息已记入本地消息表 | msgId={} | orderNo={} | reason={}",
                record.getMsgId(), order.getOrderNo(), reason);

        // ② 事务提交后再投递。
        //    绝不能在这里直接发 —— 此刻事务还没提交，一旦回滚就成了
        //    「消息已经发出去了，订单却没取消」，商品会被错误地解锁
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(record);
            }
        });
    }

    /**
     * 把「锁定超时且无有效订单」的解锁通知记入本地消息表。
     *
     * <p><b>与 {@link #recordOrderCancelled} 的区别：这条消息没有对应的订单。</b>
     * 它是兜底扫描任务（{@code staleLockScanJob}）发现「商品被锁定、订单库里却没有有效订单」
     * 时发出的救援指令。既然没有业务行要改，为什么还要走本地消息表？
     * 因为「把解锁指令可靠地送到商品域」本身就是必须保证的事：
     * 直接调 Feign 的话，调用失败就丢了，而商品会继续锁着；
     * 写进本地消息表之后，发送失败有补发任务兜底，与取消订单享有同一套可靠性。
     *
     * <p>这里用 {@code REQUIRED} 而不是 {@code MANDATORY}：调用方（定时任务）本来就没有事务，
     * 由本方法自建一个，把「插入消息记录」和「提交后投递」绑在一起。
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordStaleLockUnlock(Long productId, String reason) {
        OrderCancelledMessage message = new OrderCancelledMessage();
        message.setMsgId("stale-lock-" + productId + "-" + UUID.randomUUID().toString().replace("-", ""));
        // 用可读的伪订单号占位：消费端日志里会打印 orderNo，
        // 留空会让排查时分不清「哪来的消息」，写成 STALE-LOCK 前缀一眼可辨
        message.setOrderNo("STALE-LOCK-" + productId);
        message.setProductId(productId);
        message.setCancelTime(LocalDateTime.now());

        LocalMessage record = new LocalMessage();
        record.setMsgId(message.getMsgId());
        record.setExchange(MqConstants.TRADE_EXCHANGE);
        record.setRoutingKey(MqConstants.ORDER_CANCEL_ROUTING_KEY);
        record.setBizType(BIZ_TYPE_STALE_LOCK_UNLOCK);
        record.setBizId(String.valueOf(productId));
        record.setPayload(serialize(message));
        record.setStatus(LocalMessageStatus.PENDING);
        record.setRetryCount(0);
        record.setNextRetryTime(LocalDateTime.now());
        record.setErrorMsg("");

        localMessageMapper.insert(record);
        log.warn("发现「锁定超时且无有效订单」的商品，已记入解锁消息 | productId={} | msgId={} | 原因={}",
                productId, record.getMsgId(), reason);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish(record);
            }
        });
    }

    /**
     * 投递一条本地消息。由「事务提交后回调」或「补发任务」调用。
     *
     * <p><b>本方法正常返回并不代表 Broker 已经收到</b> —— 确认是异步的，
     * 由 Confirm 回调去更新状态。所以这里只在「发送动作本身抛异常」时
     * （例如连接不可用、序列化失败）记一次失败并安排重试；其余情况交给回调与补发任务。
     */
    public void publish(LocalMessage record) {
        try {
            OrderCancelledMessage payload =
                    objectMapper.readValue(record.getPayload(), OrderCancelledMessage.class);

            rabbitTemplate.convertAndSend(
                    record.getExchange(),
                    record.getRoutingKey(),
                    payload,
                    // 把 msgId 放进 CorrelationData：Confirm 回调靠它定位是这条本地消息。
                    // 不放的话回调里拿不到业务上下文，只能记一句「有消息没发成功」
                    new CorrelationData(record.getMsgId()));

            log.info("已投递订单取消消息，等待 Broker 确认 | msgId={} | orderNo={}",
                    record.getMsgId(), record.getBizId());
        } catch (Exception e) {
            log.error("投递订单取消消息失败，将由补发任务重试 | msgId={} | orderNo={}",
                    record.getMsgId(), record.getBizId(), e);
            markRetry(record.getMsgId(), e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 标记「Broker 已确认收到」。由 Confirm 回调调用。
     *
     * <p>带上 {@code status = 待发送} 的前置条件：万一重复回调（Broker 重发确认），
     * 第二次的受影响行数是 0，不会把已经是「已发送」的记录改坏。
     */
    public void markSent(String msgId) {
        int affected = localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getMsgId, msgId)
                .eq(LocalMessage::getStatus, LocalMessageStatus.PENDING)
                .set(LocalMessage::getStatus, LocalMessageStatus.SENT)
                .set(LocalMessage::getErrorMsg, "")
                .set(LocalMessage::getUpdateTime, LocalDateTime.now()));
        if (affected > 0) {
            log.info("订单取消消息已确认送达 Broker | msgId={}", msgId);
        }
    }

    /**
     * 记录一次投递失败，并按退避安排下次重试时间；超过上限则转为「失败待人工处理」。
     *
     * <p>由两种场景调用：发送动作抛异常（{@link #publish}）、
     * 以及 Broker 明确回 nack（Confirm 回调）。
     */
    public void markRetry(String msgId, String errorMsg) {
        LocalMessage record = localMessageMapper.selectOne(
                new LambdaQueryWrapper<LocalMessage>().eq(LocalMessage::getMsgId, msgId));
        if (record == null) {
            log.warn("找不到本地消息记录，无法安排重试 | msgId={}", msgId);
            return;
        }

        int retryCount = (record.getRetryCount() == null ? 0 : record.getRetryCount()) + 1;
        boolean exhausted = retryCount >= orderProperties.getLocalMessageMaxRetry();

        localMessageMapper.update(null, new LambdaUpdateWrapper<LocalMessage>()
                .eq(LocalMessage::getId, record.getId())
                .set(LocalMessage::getRetryCount, retryCount)
                .set(LocalMessage::getStatus,
                        exhausted ? LocalMessageStatus.FAILED : LocalMessageStatus.PENDING)
                .set(LocalMessage::getNextRetryTime,
                        exhausted ? null : LocalDateTime.now().plusSeconds(backoffSeconds(retryCount)))
                .set(LocalMessage::getErrorMsg, truncate(errorMsg))
                .set(LocalMessage::getUpdateTime, LocalDateTime.now()));

        if (exhausted) {
            // 刻意不删除、也不静默丢弃：记录本身就是排查证据
            log.error("订单取消消息重试 {} 次仍未成功，转为人工处理 | msgId={} | orderNo={} | 最后错误={}",
                    retryCount, msgId, record.getBizId(), errorMsg);
        } else {
            log.warn("订单取消消息投递失败，第 {} 次重试已排期 | msgId={} | 错误={}",
                    retryCount, msgId, errorMsg);
        }
    }

    private long backoffSeconds(int retryCount) {
        int index = Math.min(retryCount - 1, RETRY_BACKOFF_SECONDS.length - 1);
        return RETRY_BACKOFF_SECONDS[Math.max(index, 0)];
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // 序列化失败属于代码缺陷（字段类型不支持序列化），必须让事务回滚，
            // 否则会出现「业务变更提交了、消息却没记下来」
            throw new IllegalStateException("本地消息序列化失败", e);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= ERROR_MSG_MAX_LENGTH
                ? text
                : text.substring(0, ERROR_MSG_MAX_LENGTH);
    }
}
