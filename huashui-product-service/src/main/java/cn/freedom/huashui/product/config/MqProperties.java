package cn.freedom.huashui.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ 运维相关配置。
 *
 * @author freedom0213
 */
@Data
@ConfigurationProperties(prefix = "huashui.mq")
public class MqProperties {

    /**
     * RabbitMQ 管理插件（management）的 HTTP API 地址。
     *
     * <p>为什么查死信内容必须走管理 API、而不是用 AMQP：AMQP 只有「取走」（basicGet）
     * 一种读取方式，取走就把消息从队列里移除了。而「查看」的语义必须是不消费的。
     * 管理 API 的 {@code ackmode=ack_requeue_true} 正好提供了「取出后立刻放回」。
     */
    private String managementUrl = "http://127.0.0.1:15673";

    private String username = "huashui";

    private String password = "huashui@123";

    /** 虚拟主机。默认 vhost "/" 在管理 API 的路径里要写成 %2F */
    private String vhost = "/";

    /** 单次查看死信时最多取多少条 */
    private int deadLetterFetchSize = 50;

    /** 单次重放最多处理多少条 */
    private int deadLetterReplayLimit = 10;
}
