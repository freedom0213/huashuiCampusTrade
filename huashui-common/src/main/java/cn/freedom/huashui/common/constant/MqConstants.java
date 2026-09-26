package cn.freedom.huashui.common.constant;

/**
 * RabbitMQ 常量。
 *
 * <p>本项目只设计一个业务队列（订单取消 → 商品恢复在售），
 * 与其铺多个用不上的队列，不如把一个队列的可靠性做全：
 * Confirm → 持久化 → 手动 ACK → 重试 → 死信 → 消费端幂等 → 本地消息表兜底。
 *
 * @author freedom0213
 */
public final class MqConstants {

    private MqConstants() {
    }

    /** 交易主题交换机 */
    public static final String TRADE_EXCHANGE = "trade.topic";

    /** 订单取消队列：product-service 消费后把商品从「已锁定」恢复为「在售」 */
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";

    /** 订单取消路由键 */
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.canceled";

    /** 订单取消死信交换机 */
    public static final String ORDER_CANCEL_DLX = "order.cancel.dlx";

    /** 订单取消死信队列：重试耗尽后进入，人工介入排查 */
    public static final String ORDER_CANCEL_DLQ = "order.cancel.dlq";

    /** 订单取消死信路由键 */
    public static final String ORDER_CANCEL_DLQ_ROUTING_KEY = "order.cancel.dead";
}
