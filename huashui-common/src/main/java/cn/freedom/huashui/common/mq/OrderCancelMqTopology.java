package cn.freedom.huashui.common.mq;

import cn.freedom.huashui.common.constant.MqConstants;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 「订单取消 → 商品恢复在售」这条消息链路的完整拓扑。
 *
 * <pre>
 *   order-service ──publish──→ trade.topic ──order.canceled──→ order.cancel.queue ──→ 消费
 *                                                                      │
 *                                             ┌────── 消费失败 ────────┴──── 重试次数超限
 *                                             ↓                                    ↓
 *                             order.cancel.dlx → 延迟队列(1s/2s/4s)      order.cancel.dlx
 *                                             │      ↑ TTL 到期后                → order.cancel.dlq
 *                                             └──────┘ 自动回到业务队列           （人工介入）
 * </pre>
 *
 * <p><b>重试为什么用「延迟队列」这种绕法：</b>
 * RabbitMQ 本身没有延迟队列类型，业界通用做法是「队列设 {@code x-message-ttl} +
 * {@code x-dead-letter-exchange} 指回业务交换机」——消息在队列里躺够 TTL 就当死信被投回去。
 * 这样不需要引入额外的延迟插件，也不需要让消费者线程 {@code sleep}（那会占住消费线程）。
 * 代价是每个延迟档位要单独一个队列（TTL 是队列级参数，不能按消息设——按消息设会有队头阻塞）。
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
 * <p><b>为什么拓扑在阶段 7 就一次声明到位：</b>
 * <b>队列的参数在创建后不可修改</b>。缺了 {@code x-dead-letter-exchange} 或 TTL，
 * 后期再补就得删掉队列重建，而那时队列里可能已经积压了消息。
 * 所以阶段 7 就配好死信、阶段 9 再加延迟重试队列（新增队列是安全的，改才不安全）。
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
     * 各档延迟重试队列及其绑定。
     *
     * <p>用 {@link Declarables} 一次声明而不是写 N 个 {@code @Bean}：
     * 三个队列的差别只有 TTL 和名字，写成三个方法就是三份复制粘贴，
     * 而「复制出来的副本会慢慢和原件不一致」这件事在 阶段 7 已经吃过一次（ProductConverter 的由来）。
     * 现在档位由 {@link MqConstants#ORDER_CANCEL_RETRY_DELAYS_MS} 一处决定。
     *
     * <p>每个队列的两个关键参数：
     * <ul>
     *   <li>{@code x-message-ttl} —— 消息在此队列等待多久（即本轮重试的退避时间）；</li>
     *   <li>{@code x-dead-letter-exchange/routing-key} —— TTL 到期后送回<b>业务交换机</b>，
     *       于是消息重新进入 {@code order.cancel.queue} 被再次消费。</li>
     * </ul>
     *
     * @param orderCancelDlx 死信交换机（延迟队列从这里接收消息）
     * @return 批量声明的队列与绑定
     */
    @Bean
    public Declarables orderCancelRetryDeclarables(DirectExchange orderCancelDlx) {
        List<Declarable> declarables = new ArrayList<>();
        for (int level = 1; level <= MqConstants.ORDER_CANCEL_MAX_RETRY; level++) {
            long delayMs = MqConstants.ORDER_CANCEL_RETRY_DELAYS_MS[level - 1];
            Queue queue = QueueBuilder.durable(MqConstants.orderCancelRetryQueue(level))
                    .ttl((int) delayMs)
                    // TTL 到期后送回业务交换机，而不是送回死信队列 ——
                    // 送回死信队列就等于直接放弃了重试
                    .deadLetterExchange(MqConstants.TRADE_EXCHANGE)
                    .deadLetterRoutingKey(MqConstants.ORDER_CANCEL_ROUTING_KEY)
                    .build();
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue)
                    .to(orderCancelDlx)
                    .with(MqConstants.orderCancelRetryRoutingKey(level)));
        }
        return new Declarables(declarables);
    }

    /**
     * 消息体专用的 ObjectMapper，与 {@link #jsonMessageConverter()} 用同一份配置。
     *
     * <p>单独立一个 Bean 是被验证环节逼出来的：死信重放要解析消息体，
     * 注入容器里那个 ObjectMapper 的话——它把 LocalDateTime 定成 {@code yyyy-MM-dd HH:mm:ss}
     * （为浏览器前端定的），而消息转换器写的是 ISO-8601。格式对不上，
     * 「自己服务发出的消息自己解析不了」，重放功能整体失效。
     * 消息体是服务间契约，读写必须同一份规则。
     *
     * @return 消息体专用的 ObjectMapper
     */
    @Bean
    public ObjectMapper mqObjectMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        return mapper;
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
    public MessageConverter jsonMessageConverter(ObjectMapper mqObjectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mqObjectMapper);
        // 注意导入位置：TypePrecedence 是 Jackson2JavaTypeMapper 的内部枚举，
        // 不在 Jackson2JsonMessageConverter 里；而 setTypePrecedence 由
        // AbstractJackson2MessageConverter 提供。写错位置编译期就报「找不到符号」
        converter.setTypePrecedence(Jackson2JavaTypeMapper.TypePrecedence.INFERRED);
        return converter;
    }
}
