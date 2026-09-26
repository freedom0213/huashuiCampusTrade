package cn.freedom.huashui.product.mq;

import cn.freedom.huashui.common.api.mq.OrderCancelledMessage;
import cn.freedom.huashui.common.constant.MqConstants;
import cn.freedom.huashui.common.constant.RedisKeys;
import cn.freedom.huashui.product.cache.CacheService;
import cn.freedom.huashui.product.service.ProductService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 「订单已取消」消息消费者：把商品从「已锁定」恢复为「在售」。
 *
 * <p><b>三层保护，各管一段：</b>
 * <ol>
 *   <li><b>手动 ACK</b> —— 处理成功才确认。进程中途挂掉时消息仍在队列里，
 *       会被重新投递，不会丢。</li>
 *   <li><b>有限重试</b> —— 失败不直接丢死信，而是投到延迟队列，
 *       等 1s / 2s / 4s 再试；耗尽才进死信。多数「数据库瞬时抖动」在第 1 次重试就恢复了。</li>
 *   <li><b>幂等</b> —— 业务侧的条件更新天然幂等（重复执行第二次受影响行数为 0），
 *       外加 msgId 去重来减少无效执行。</li>
 * </ol>
 *
 * <p><b>为什么重试不用 Spring 框架自带的 {@code listener.retry}：</b>
 * 那是在监听器内部循环重试，不产生新的消息。配合手动 ACK 时，
 * 「重试到第几次」与「最终 ack 还是 nack」之间的对应关系不直观，
 * 排查只能靠日志。这里改成显式投递到延迟队列 ——
 * <b>每一次重试在 RabbitMQ 里都是一条真实的消息</b>，管理台上能看见、能数清。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelListener {

    /** 幂等标记的保留时长。取 24 小时：远长于消息可能的重投窗口，又不会长期占内存 */
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final ProductService productService;
    private final CacheService cacheService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstants.ORDER_CANCEL_QUEUE)
    public void onOrderCancelled(Message rawMessage,
                                 @Payload OrderCancelledMessage message,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        String msgId = message.getMsgId();
        String dedupKey = RedisKeys.mqConsumed(msgId);

        // ① 幂等前置检查：见过这条消息就直接确认掉，不再执行业务
        if (!cacheService.tryMarkProcessed(dedupKey, DEDUP_TTL)) {
            channel.basicAck(deliveryTag, false);
            log.info("重复消息，跳过处理并确认 | msgId={} | orderNo={}", msgId, message.getOrderNo());
            return;
        }

        try {
            // ② 业务处理。unlockForOrder 是条件更新、天然幂等，
            //    所以即使去重标记因为任何原因失效，也不会造成数据错乱 —— 去重只是省一次无用写
            productService.unlockForOrder(message.getProductId());
            channel.basicAck(deliveryTag, false);
            log.info("订单取消消息处理完成，商品已恢复在售 | orderNo={} | productId={}",
                    message.getOrderNo(), message.getProductId());
        } catch (Exception e) {
            // ③ 业务失败必须撤销去重标记。
            //    否则重投时会被 ① 判成「已处理」直接跳过 —— 消息被永久丢弃且不报任何错
            cacheService.clearMark(dedupKey);
            handleFailure(rawMessage, message, channel, deliveryTag, e);
        }
    }

    /**
     * 处理消费失败：还没到重试上限就投到延迟队列，到上限才送死信。
     */
    private void handleFailure(Message rawMessage,
                               OrderCancelledMessage message,
                               Channel channel,
                               long deliveryTag,
                               Exception cause) throws IOException {
        Object header = rawMessage.getMessageProperties().getHeader(MqConstants.HEADER_RETRY_COUNT);
        int retriedCount = header instanceof Integer value ? value : 0;

        if (retriedCount < MqConstants.ORDER_CANCEL_MAX_RETRY) {
            int nextLevel = retriedCount + 1;
            try {
                // 投到第 nextLevel 档延迟队列：该队列的 TTL 到期后会把消息送回业务队列。
                // 于是「等一会儿再重试」既不需要 sleep 阻塞消费线程，也不需要额外的调度器
                rabbitTemplate.convertAndSend(
                        MqConstants.ORDER_CANCEL_DLX,
                        MqConstants.orderCancelRetryRoutingKey(nextLevel),
                        message,
                        props -> {
                            props.getMessageProperties()
                                    .setHeader(MqConstants.HEADER_RETRY_COUNT, nextLevel);
                            return props;
                        });
                // 原消息已经重发了，确认掉它，避免它在业务队列里越堆越多
                channel.basicAck(deliveryTag, false);
                log.warn("订单取消消息处理失败，{}ms 后进行第 {} 次重试 | orderNo={} | 原因={}",
                        MqConstants.ORDER_CANCEL_RETRY_DELAYS_MS[nextLevel - 1],
                        nextLevel, message.getOrderNo(), cause.getMessage());
                return;
            } catch (Exception sendException) {
                log.error("重试消息投递失败，直接转入死信队列 | orderNo={}",
                        message.getOrderNo(), sendException);
            }
        }

        // 重试次数用尽（或重投本身失败）→ 进死信队列
        log.error("订单取消消息重试 {} 次仍失败，转入死信队列等待人工处理 | orderNo={} | productId={}",
                MqConstants.ORDER_CANCEL_MAX_RETRY, message.getOrderNo(), message.getProductId(), cause);
        // requeue = false：不把消息塞回队列。失败若是持久性的（数据异常、代码缺陷），
        // 重回队列只会形成「失败 → 重投 → 再失败」的死循环，把消费线程占满并刷爆日志
        channel.basicNack(deliveryTag, false, false);
    }
}
