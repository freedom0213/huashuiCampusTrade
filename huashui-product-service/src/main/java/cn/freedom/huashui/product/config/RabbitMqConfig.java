package cn.freedom.huashui.product.config;

import cn.freedom.huashui.common.mq.OrderCancelMqTopology;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 引入订单取消相关的 MQ 拓扑。
 *
 * <p>拓扑定义在 common 的 {@link OrderCancelMqTopology}：生产方（order）与消费方（product）
 * 声明的参数必须完全一致，否则 RabbitMQ 会抛 {@code PRECONDITION_FAILED} 并断开连接。
 * 共用同一份实现是杜绝这种不一致最可靠的办法。
 *
 * <p>这里用 {@code @Import} 而不是自动配置，是为了让「本服务会声明哪些队列」
 * 在服务自己的配置里就能看到（可 grep），而不是藏在 jar 包的自动配置里。
 *
 * @author freedom0213
 */
@Configuration
@Import(OrderCancelMqTopology.class)
public class RabbitMqConfig {
}
