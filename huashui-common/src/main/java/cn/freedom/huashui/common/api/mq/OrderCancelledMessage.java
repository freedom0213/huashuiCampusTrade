package cn.freedom.huashui.common.api.mq;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单已取消的消息体 —— order 域发出，product 域消费。
 *
 * <p>消费动作：把商品从「已锁定」恢复为「在售」。
 *
 * <p><b>消息用 JSON 而不是 Java 原生序列化</b>：
 * 原生序列化把类的全限定名写进消息，消费端一改包名就反序列化失败；
 * 而且它允许反序列化任意类，是已知的反序列化漏洞来源。JSON 两者都没有。
 *
 * <p><b>为什么必须带 msgId：</b>
 * MQ 的投递语义是「至少一次」，重复消息一定会出现。msgId 是消费端做幂等去重的依据
 * （阶段 9 会在 product 侧用 Redis 记录已处理的 msgId）。
 * 即便不做去重，product 侧的条件更新也天然幂等，这里是双重保险。
 *
 * @author freedom0213
 */
@Data
public class OrderCancelledMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一 id，消费端据此幂等 */
    private String msgId;

    /** 订单号，便于日志追踪与人工排查 */
    private String orderNo;

    /** 需要恢复为「在售」的商品 id */
    private Long productId;

    /** 买家 id（日志用） */
    private Long buyerId;

    /** 卖家 id（日志用） */
    private Long sellerId;

    /** 取消时间 */
    private LocalDateTime cancelTime;
}
