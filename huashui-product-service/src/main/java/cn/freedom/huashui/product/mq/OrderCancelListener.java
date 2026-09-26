package cn.freedom.huashui.product.mq;

import cn.freedom.huashui.common.api.mq.OrderCancelledMessage;
import cn.freedom.huashui.common.constant.MqConstants;
import cn.freedom.huashui.product.service.ProductService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 「订单已取消」消息消费者：把商品从「已锁定」恢复为「在售」。
 *
 * <p><b>为什么取消走 MQ 而不是同步调用：</b>
 * 取消是一个「回退」动作，允许最终一致。走 MQ 后，取消接口的响应时间不再取决于
 * 商品服务的健康状况，且消息自带重投能力。而付款走的是同步调用——
 * 钱货两清的关键动作必须立刻拿到结果。判断依据是<b>失败后果的严重性</b>。
 *
 * <p><b>幂等靠两层保障：</b>
 * ① {@code unlockForOrder} 内部是条件更新（{@code WHERE status = 已锁定}），
 * 重复执行第二次受影响行数为 0，不产生副作用；
 * ② 消息体带 {@code msgId}，阶段 9 会在消费前用 Redis 记录已处理的 msgId 做前置去重。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelListener {

    private final ProductService productService;

    /**
     * 消费订单取消消息。
     *
     * <p>手动 ACK 模式（{@code spring.rabbitmq.listener.simple.acknowledge-mode: manual}）：
     * 由代码显式告诉 Broker「这条消息我处理完了」，而不是方法一返回就自动确认。
     * 只有在「确认」之后消息才会从队列里真正消失，因此进程中途挂掉不会丢消息。
     */
    @RabbitListener(queues = MqConstants.ORDER_CANCEL_QUEUE)
    public void onOrderCancelled(OrderCancelledMessage message,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("收到订单取消消息 | orderNo={} | productId={} | msgId={}",
                message.getOrderNo(), message.getProductId(), message.getMsgId());
        try {
            productService.unlockForOrder(message.getProductId());
            channel.basicAck(deliveryTag, false);
            log.info("订单取消消息处理完成，商品已恢复在售 | orderNo={} | productId={}",
                    message.getOrderNo(), message.getProductId());
        } catch (Exception e) {
            // requeue = false：不要立刻把消息塞回队列。
            // 若失败原因是持久性的（数据异常、代码缺陷），重回队列会形成
            // 「失败 → 重投 → 再失败」的死循环，把消费线程占满并刷爆日志。
            // 交给死信队列，让它可见、可查、可人工重放。
            log.error("订单取消消息处理失败，将转入死信队列 | orderNo={} | productId={}",
                    message.getOrderNo(), message.getProductId(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
