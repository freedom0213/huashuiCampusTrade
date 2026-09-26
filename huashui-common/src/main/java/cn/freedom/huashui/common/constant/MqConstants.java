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

    // ==================== 消费失败的重试（延迟队列） ====================

    /**
     * 重试队列名前缀。第 n 档队列名 = 前缀 + n。
     *
     * <p>做法是「延迟队列」：消息投进第 n 档队列后不会立刻被消费，
     * 而是等该队列的 {@code x-message-ttl} 到期，再被死信机制送回业务队列重投。
     * 这是 RabbitMQ 实现「延迟重试」的常规手法（本身不提供延迟队列类型）。
     */
    public static final String ORDER_CANCEL_RETRY_QUEUE_PREFIX = "order.cancel.retry.queue.";

    /** 重试队列的路由键前缀，绑定在死信交换机上 */
    public static final String ORDER_CANCEL_RETRY_ROUTING_KEY_PREFIX = "order.cancel.retry.";

    /**
     * 消息头：已重试次数。
     *
     * <p>为什么不用 RabbitMQ 自带的 {@code x-death} 计数：它统计的是「进过死信队列」的次数，
     * 而我们的重试走的是延迟队列、并非每次都进死信，语义对不上。用自定义头更直白。
     */
    public static final String HEADER_RETRY_COUNT = "x-retry-count";

    /**
     * 各档重试延迟（毫秒）：1s → 2s → 4s。
     *
     * <p>用递增退避而不是固定间隔：消费失败多半是「下游瞬时不可用」，
     * 第一次重试给 1 秒就够；如果 1 秒后还没恢复，说明问题更大，多等一会儿更合理，
     * 也避免快速重试把下游压得更死。
     */
    public static final long[] ORDER_CANCEL_RETRY_DELAYS_MS = {1000L, 2000L, 4000L};

    /** 重试档位数，即最大重试次数 */
    public static final int ORDER_CANCEL_MAX_RETRY = ORDER_CANCEL_RETRY_DELAYS_MS.length;

    public static String orderCancelRetryQueue(int level) {
        return ORDER_CANCEL_RETRY_QUEUE_PREFIX + level;
    }

    public static String orderCancelRetryRoutingKey(int level) {
        return ORDER_CANCEL_RETRY_ROUTING_KEY_PREFIX + level;
    }
}
