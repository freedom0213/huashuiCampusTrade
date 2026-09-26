package cn.freedom.huashui.product.mq;

import cn.freedom.huashui.common.api.mq.OrderCancelledMessage;
import cn.freedom.huashui.common.constant.MqConstants;
import cn.freedom.huashui.product.config.MqProperties;
import cn.freedom.huashui.product.vo.DeadLetterItemVO;
import cn.freedom.huashui.product.vo.DeadLetterVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 死信队列的查看与重放。
 *
 * <p><b>为什么要把这个做成接口，而不是让人去管理台点：</b>
 * 管理台是运维视角的东西 —— 它能看、能重放，但没法接进告警、接进管理端页面、
 * 也没法写进自动化脚本。把死信做成可编程访问的能力，
 * 才谈得上「消息出了问题能被系统发现」，而不只是「被人偶然看到」。
 *
 * <p><b>「查看」为什么不消费消息：</b>AMQP 协议只有 basicGet 一种读取方式，
 * 取走即移除，用它做「查看」等于看一眼就把消息弄丢了。
 * 所以内容走管理 API 的 {@code ackmode=ack_requeue_true}（取出后立刻放回），
 * 而积压条数走 AMQP —— 后者不依赖管理插件，管理 API 挂了也能拿到最关键的信息。
 *
 * @author freedom0213
 */
@Slf4j
@Service
public class MqDeadLetterService {

    /** 单条 receive 的等待时间：队列空了就尽快返回，不阻塞接口 */
    private static final long RECEIVE_TIMEOUT_MS = 300L;

    private final MqProperties mqProperties;
    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;
    private final ObjectMapper objectMapper;
    private final RestTemplateBuilder restTemplateBuilder;

    private RestTemplate restTemplate;

    /**
     * ⚠️ ObjectMapper 必须用 {@code mqObjectMapper}（与消息转换器同一份），
     * 而不是容器里那个：容器 mapper 的 LocalDateTime 格式是给前端定的
     * {@code yyyy-MM-dd HH:mm:ss}，消息体写的是 ISO-8601。拿错的话，
     * 「自己服务发出的消息自己解析不了」，重放功能整体失效——验证环节实测踩中过。
     */
    public MqDeadLetterService(MqProperties mqProperties,
                               RabbitTemplate rabbitTemplate,
                               AmqpAdmin amqpAdmin,
                               @Qualifier("mqObjectMapper") ObjectMapper objectMapper,
                               RestTemplateBuilder restTemplateBuilder) {
        this.mqProperties = mqProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
        this.objectMapper = objectMapper;
        this.restTemplateBuilder = restTemplateBuilder;
    }

    @PostConstruct
    void initRestTemplate() {
        this.restTemplate = restTemplateBuilder
                .basicAuthentication(mqProperties.getUsername(), mqProperties.getPassword())
                // 超时必须设：管理 API 不可达时不该把接口一起拖住
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * 查看死信队列。消息被取出后立刻放回，不产生任何消费。
     *
     * @param count 期望查看的条数，&le;0 表示用配置里的默认值
     */
    public DeadLetterVO peekDeadLetters(int count) {
        String queue = MqConstants.ORDER_CANCEL_DLQ;
        int depth = queueDepth(queue);
        int fetchSize = count > 0
                ? Math.min(count, mqProperties.getDeadLetterFetchSize())
                : mqProperties.getDeadLetterFetchSize();

        String url = mqProperties.getManagementUrl() + "/api/queues/" + encodeVhost() + "/" + queue + "/get";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", fetchSize);
        // ack_requeue_true = 取出后重新入队。用 false 会真的消费掉消息，
        // 一个「查看」动作把消息弄丢，属于运维事故
        body.put("ackmode", "ack_requeue_true");
        body.put("encoding", "auto");
        body.put("truncate", 50000);

        try {
            // ⚠️ 必须包成 java.net.URI 再传：直接传 String 的话 RestTemplate 会把
            // %2F 再次编码成 %252F，服务端解出字面量 "%2F" 当 vhost 名 → vhost_not_found。
            // 传 URI 对象则原样发送，不做任何再编码（验证环节实测踩中过）
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.POST, new HttpEntity<>(body),
                    new ParameterizedTypeReference<>() {
                    });
            List<Map<String, Object>> raw = response.getBody();
            List<DeadLetterItemVO> items = raw == null
                    ? List.of()
                    : raw.stream().map(this::toItem).toList();
            return new DeadLetterVO(queue, depth, items, true, "");
        } catch (Exception e) {
            // 管理插件没开、或地址配错时不该让接口报 500 ——
            // 「队列里积压了几条」这个最关键的信息仍然能给出，把能给的先给出去
            log.warn("调用 RabbitMQ 管理 API 失败 | url={}", url, e);
            return new DeadLetterVO(queue, depth, List.of(), false,
                    "管理 API 不可用（" + e.getMessage() + "）。"
                            + "积压条数仍然可用；消息内容请登录管理台查看，或直接调用重放接口");
        }
    }

    /**
     * 重放死信消息：从死信队列取出，重新投递到业务交换机，让消费端再处理一次。
     *
     * @param limit 本次最多重放多少条，&le;0 表示用配置里的默认值
     * @return 实际重放成功的条数
     */
    public int replayDeadLetters(int limit) {
        String queue = MqConstants.ORDER_CANCEL_DLQ;
        int max = limit > 0
                ? Math.min(limit, mqProperties.getDeadLetterReplayLimit())
                : mqProperties.getDeadLetterReplayLimit();

        int replayed = 0;
        for (int i = 0; i < max; i++) {
            Message message = rabbitTemplate.receive(queue, RECEIVE_TIMEOUT_MS);
            if (message == null) {
                break;   // 队列已空
            }
            try {
                // 重新构造一条消息投出去，而不是转发原消息：
                // 原消息带着 x-retry-count = 3（已耗尽），直接转发会被消费端立刻判为超限、
                // 又一次进死信 —— 那样重放就变成了原地打转
                OrderCancelledMessage payload = objectMapper.readValue(
                        new String(message.getBody(), StandardCharsets.UTF_8),
                        OrderCancelledMessage.class);
                rabbitTemplate.convertAndSend(
                        MqConstants.TRADE_EXCHANGE,
                        MqConstants.ORDER_CANCEL_ROUTING_KEY,
                        payload);
                replayed++;
            } catch (Exception e) {
                // 消息已经被 receive 取走（basicGet 即确认），所以失败时必须主动放回，
                // 否则「重放」这个动作反而把消息弄丢了
                log.error("重放死信消息失败，尝试放回死信队列 | body={}",
                        new String(message.getBody(), StandardCharsets.UTF_8), e);
                try {
                    rabbitTemplate.send(MqConstants.ORDER_CANCEL_DLX,
                            MqConstants.ORDER_CANCEL_DLQ_ROUTING_KEY, message);
                } catch (Exception sendException) {
                    log.error("放回死信队列也失败，消息内容已记录在上一条日志里，请人工恢复", sendException);
                }
            }
        }
        log.info("死信重放完成 | 本次重放 {} 条", replayed);
        return replayed;
    }

    /** 队列积压条数。走 AMQP，不依赖管理插件 */
    private int queueDepth(String queue) {
        try {
            Properties properties = amqpAdmin.getQueueProperties(queue);
            if (properties == null) {
                return 0;
            }
            // ⚠️ 常量在 RabbitAdmin 上而非 AmqpAdmin 接口 —— 编译期踩过一次，
            //    javap 确认后修正。getQueueProperties 返回的 key 就是这个常量
            Object count = properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
            return count instanceof Number number ? number.intValue() : 0;
        } catch (Exception e) {
            log.warn("获取队列深度失败 | queue={}", queue, e);
            return 0;
        }
    }

    private String encodeVhost() {
        // 默认 vhost "/" 在管理 API 的路径里必须写成 %2F
        return "/".equals(mqProperties.getVhost()) ? "%2F" : mqProperties.getVhost();
    }

    @SuppressWarnings("unchecked")
    private DeadLetterItemVO toItem(Map<String, Object> raw) {
        DeadLetterItemVO item = new DeadLetterItemVO();
        String payload = (String) raw.get("payload");
        item.setPayload(payload);

        // 从消息体里把业务字段提出来：整条 payload 给人工看太长，
        // 直接给出订单号与商品 id 才能一眼定位是哪笔交易出了问题
        try {
            OrderCancelledMessage message = objectMapper.readValue(payload, OrderCancelledMessage.class);
            item.setMsgId(message.getMsgId());
            item.setOrderNo(message.getOrderNo());
            item.setProductId(message.getProductId());
            item.setCancelTime(message.getCancelTime());
        } catch (Exception e) {
            log.warn("死信消息体解析失败，仅返回原文 | payload={}", payload);
        }

        // x-death 由 RabbitMQ 自动维护，记录「因为什么、第几次」进入死信
        Object properties = raw.get("properties");
        if (properties instanceof Map<?, ?> props && props.get("headers") instanceof Map<?, ?> headers) {
            Object death = headers.get("x-death");
            if (death instanceof List<?> deaths && !deaths.isEmpty()
                    && deaths.get(0) instanceof Map<?, ?> first) {
                item.setDeathReason(String.valueOf(first.get("reason")));
                Object count = first.get("count");
                item.setDeathCount(count instanceof Number number ? number.intValue() : null);
            }
        }
        return item;
    }
}
