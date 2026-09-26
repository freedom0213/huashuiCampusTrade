package cn.freedom.huashui.order.config;

import cn.freedom.huashui.common.mq.OrderCancelMqTopology;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 引入订单取消相关的 MQ 拓扑。
 *
 * <p>拓扑定义在 common 的 {@link OrderCancelMqTopology}。本服务是这条消息的<b>生产方</b>，
 * 同样必须声明交换机与队列：
 * 不声明交换机时，向不存在的交换机发消息会直接失败；
 * 只声明交换机、不声明队列与绑定时更危险——topic 交换机找不到匹配队列时会
 * <b>静默丢弃消息</b>，不报任何错。
 *
 * <p>与 product 共用同一份实现，也顺带保证了「两边声明的参数完全一致」。
 *
 * @author freedom0213
 */
@Configuration
@Import(OrderCancelMqTopology.class)
public class RabbitMqConfig {
}
