package cn.freedom.huashui.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 交易服务启动类。
 *
 * <p>端口 6004，通过网关（6001）对外提供服务。网关路由：{@code /api/order/**}。
 *
 * <p>{@code @EnableFeignClients} 的扫描包指向 <b>common 的 api 包</b>，
 * 而不是本服务的包：Feign 接口与 DTO 是跨服务的契约，统一放在 common，
 * 保证「调用方发出的字段」和「提供方接收的字段」来自同一份定义。
 *
 * <p>{@code @EnableScheduling} 用于超时未付款订单的兜底清理任务
 * （正式方案是阶段 10 的 XXL-JOB，届时停用本地任务即可）。
 *
 * @author freedom0213
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.freedom.huashui.common.api")
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("cn.freedom.huashui.order.mapper")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
