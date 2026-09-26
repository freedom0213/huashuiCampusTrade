package cn.freedom.huashui.common.mq;

import cn.freedom.huashui.common.constant.MqConstants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 「订单取消 → 商品恢复在售」这条消息链路的完整拓扑。
 *
 * <pre>
 *   order-service ──publish──→ trade.topic ──order.canceled──→ order.cancel.queue ──→ 消费
 *                                                                      │
 *                                                          消费失败 nack(requeue=false)
 *                                                                      ↓
 *                                            order.cancel.dlx ──→ order.cancel.dlq（人工介入）
 * </pre>
 *
 * <p><b>为什么放在 common，而不是各自声明一份：</b>
 * 生产方（order）与消费方（product）都必须在自己的连接上声明拓扑——生产方不声明，
 * 交换机不存在时消息会被静默丢弃（topic 交换机找不到匹配队列不会报错）；
 * 消费方不声明，则消费者启动时会因队列缺失而反复重连。
 * 但两边声明的<b>参数必须完全一致</b>，否则 RabbitMQ 会抛
 * {@code PRECONDITION_FAILED - inequivalent arg} 并直接断开连接，
 * 报错信息还相当难懂。让两边共用同一份实现，是杜绝这种不一致最可靠的办法。
 *
 * <p><b>引入方式：各服务在自己的 {@code @Configuration} 上 {@code @Import} 本类</b>，
 * 而不是做成 SPI 自动配置——「启动时创建队列」是有副作用的动作，
 * 让它显式出现在服务自己的配置里（能 grep 到），比藏在自动配置中更容易排查。
 *
 * <p><b>为什么现在就配死信队列（消息可靠性是阶段 9 的事）：</b>
 * <b>队列的参数在创建后不可修改</b>。现在不把 {@code x-dead-letter-exchange} 写上，
 * 阶段 9 就得删掉队列重建，而那时队列里可能已经积压了消息。
 * 所以拓扑要一次声明到位；阶段 9 补的是「发送侧」的可靠性
 * （Confirm 回调 / 本地消息表 / 退避重试），与拓扑无关。
 *
 * @author freedom0213
 */
@Configuration
public class OrderCancelMqTopology {

    /** 业务主题交换机 */
    @Bean
    public TopicExchange tradeExchange() {
        return new TopicExchange(MqConstants.TRADE_EXCHANGE, true, false);
    }

    /** 订单取消队列，绑定死信交换机：消息被拒绝后自动转入死信队列 */
    @Bean
    public Queue orderCancelQueue() {
        return QueueBuilder.durable(MqConstants.ORDER_CANCEL_QUEUE)
                .deadLetterExchange(MqConstants.ORDER_CANCEL_DLX)
                .deadLetterRoutingKey(MqConstants.ORDER_CANCEL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderCancelBinding(Queue orderCancelQueue, TopicExchange tradeExchange) {
        return BindingBuilder.bind(orderCancelQueue)
                .to(tradeExchange)
                .with(MqConstants.ORDER_CANCEL_ROUTING_KEY);
    }

    /** 死信交换机。用 direct 就够：死信的去向是确定的，不需要主题匹配 */
    @Bean
    public DirectExchange orderCancelDlx() {
        return new DirectExchange(MqConstants.ORDER_CANCEL_DLX, true, false);
    }

    /** 死信队列。进这里的消息需要人工排查，不做自动重试——自动重试只会掩盖问题 */
    @Bean
    public Queue orderCancelDlq() {
        return QueueBuilder.durable(MqConstants.ORDER_CANCEL_DLQ).build();
    }

    @Bean
    public Binding orderCancelDlqBinding(Queue orderCancelDlq, DirectExchange orderCancelDlx) {
        return BindingBuilder.bind(orderCancelDlq)
                .to(orderCancelDlx)
                .with(MqConstants.ORDER_CANCEL_DLQ_ROUTING_KEY);
    }

    /**
     * JSON 消息转换器。
     *
     * <p><b>刻意新建一个独立的 ObjectMapper，而不是注入 Spring 容器里那个。</b>
     * 容器里的 ObjectMapper 被 common 定制过：出于「雪花 ID 超出 JS 安全整数范围」的考虑，
     * 它把 {@code Long} 一律序列化成字符串。那是<b>为浏览器前端</b>做的妥协，
     * 而消息体是<b>服务间契约</b>，不该被前端的限制牵着走。
     *
     * <p>再来是 {@code TypePrecedence.INFERRED}：按监听方法的参数类型反序列化，
     * 忽略消息头里的 {@code __TypeId__}。默认行为是信任消息头里的类全限定名，
     * 一旦以后重命名或移动了消息类，历史消息就会解析失败并卡在队列里。
     *
     * @return JSON 消息转换器
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                // LocalDateTime 写成 ISO-8601 字符串，而不是默认的时间戳数组
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 消费端版本落后于生产端时（消息里多了字段）不应直接失败，便于两端分批上线
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        // 注意导入位置：TypePrecedence 是 Jackson2JavaTypeMapper 的内部枚举，
        // 不在 Jackson2JsonMessageConverter 里；而 setTypePrecedence 由
        // AbstractJackson2MessageConverter 提供。写错位置编译期就报「找不到符号」
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
