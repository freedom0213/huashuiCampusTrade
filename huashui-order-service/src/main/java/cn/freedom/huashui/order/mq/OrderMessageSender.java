package cn.freedom.huashui.order.mq;

import cn.freedom.huashui.common.api.mq.OrderCancelledMessage;
import cn.freedom.huashui.common.constant.MqConstants;
import cn.freedom.huashui.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 订单消息发送器。
 *
 * @author freedom0213
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送「订单已取消」消息，通知商品域把商品恢复为在售。
     *
     * <p><b>为什么不把异常抛出去：</b>
     * 此刻订单已经取消（数据库已提交）。若在这里抛异常，接口会返回失败，
     * 用户看到失败就会重试，而重试时订单已是「已取消」状态，会被
     * 「只有待支付的订单可以取消」挡回去——用户彻底困惑。
     *
     * <p><b>那反过来，发送失败时把订单回滚回待支付行不行？也不行：</b>
     * 如果消息其实已经投递成功、只是客户端没拿到确认（网络抖动），
     * 回滚会造成「订单还在，商品却被解锁」——商品可能被第二个人买走，形成超卖。
     * 两个错误里，超卖比「商品多锁一会儿」严重得多。
     *
     * <p>所以这里的态度是：只记 ERROR 日志，把「消息一定会发出去」这件事
     * 交给阶段 9 的本地消息表（把发消息也当成一条本地数据，与订单变更同事务落库，
     * 再由定时任务扫描补发）。发送侧的 Confirm 回调也在阶段 9 一并加上。
     */
    public void sendOrderCancelled(Order order, String reason) {
        OrderCancelledMessage message = new OrderCancelledMessage();
        message.setMsgId(UUID.randomUUID().toString().replace("-", ""));
        message.setOrderNo(order.getOrderNo());
        message.setProductId(order.getProductId());
        message.setBuyerId(order.getBuyerId());
        message.setSellerId(order.getSellerId());
        message.setCancelTime(LocalDateTime.now());

        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.TRADE_EXCHANGE,
                    MqConstants.ORDER_CANCEL_ROUTING_KEY,
                    message);
            log.info("已发送订单取消消息 | orderNo={} | productId={} | msgId={} | reason={}",
                    order.getOrderNo(), order.getProductId(), message.getMsgId(), reason);
        } catch (Exception e) {
            // 阶段 9 会由本地消息表兜底重发；在此之前需要靠这条日志人工发现
            log.error("订单取消消息发送失败，商品将保持锁定，需人工介入 | orderNo={} | productId={}",
                    order.getOrderNo(), order.getProductId(), e);
        }
    }
}
