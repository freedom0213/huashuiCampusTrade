package cn.freedom.huashui.order.config;

import cn.freedom.huashui.order.mq.OrderMessageSender;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

/**
 * 发送方确认（Publisher Confirm）与路由失败（Returns）回调。
 *
 * <p><b>这两个回调覆盖的是两种完全不同的发送失败：</b>
 * <table border="1">
 *   <tr><th>回调</th><th>含义</th><th>典型成因</th><th>处理</th></tr>
 *   <tr>
 *     <td>Confirm（ack=false）</td>
 *     <td>消息<b>没到 Broker</b></td>
 *     <td>网络抖动、Broker 磁盘满、队列被限流</td>
 *     <td>保持「待发送」，交给补发任务</td>
 *   </tr>
 *   <tr>
 *     <td>Returns</td>
 *     <td>消息到了 Broker，但<b>路由不到任何队列</b></td>
 *     <td>交换机或路由键写错、队列被误删</td>
 *     <td>记 ERROR 日志 —— 这是<b>配置错误</b>，重试一万次也没用</td>
 *   </tr>
 * </table>
 *
 * <p>把 Returns 与 Confirm 混为一谈是常见误解：Confirm 的 ack 只代表
 * 「Broker 收下了」，<b>不代表消息进了队列</b>。只配 Confirm 不配 Returns，
 * 交换机和路由键写错时会拿不到任何信号 —— 消息被静默丢弃，日志干干净净。
 *
 * <p>注意 Returns 回调必须配合 {@code spring.rabbitmq.template.mandatory: true} 才会触发，
 * 否则 Broker 直接丢弃不可路由的消息、不回退。
 *
 * @author freedom0213
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RabbitConfirmConfig {

    private final RabbitTemplate rabbitTemplate;
    private final OrderMessageSender messageSender;

    @PostConstruct
    public void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null || correlationData.getId() == null) {
                // 理论上不会发生：我们在 OrderMessageSender 里一定放了 CorrelationData。
                // 真发生了说明有别的代码在直接调 RabbitTemplate，值得知道
                log.warn("收到无法关联业务记录的发送确认 | ack={} | cause={}", ack, cause);
                return;
            }
            String msgId = correlationData.getId();
            if (ack) {
                messageSender.markSent(msgId);
            } else {
                // nack：Broker 明确说没收下。不改状态、只安排重试，等补发任务再试
                messageSender.markRetry(msgId, "Broker 未确认（nack）：" + cause);
            }
        });

        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "消息路由失败，已退回。这是配置问题而非偶发故障，请检查交换机与路由键 | "
                        + "exchange={} | routingKey={} | replyCode={} | replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));
    }
}
