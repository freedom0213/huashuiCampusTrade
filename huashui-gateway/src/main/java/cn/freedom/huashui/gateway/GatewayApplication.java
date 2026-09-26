package cn.freedom.huashui.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类。
 *
 * <p>{@code @ConfigurationPropertiesScan} 用于扫描 {@code cn.freedom.huashui.gateway}
 * 包下的配置属性类（白名单等），避免逐个 {@code @EnableConfigurationProperties}。
 *
 * <p><b>注意本类不做组件扫描扩展</b>：huashui-common 的 Bean 全部通过 SPI 自动配置加载。
 * 若在此把扫描范围扩大到 {@code cn.freedom.huashui}，会把 common 中 Servlet 专用的
 * {@code GlobalExceptionHandler} 也扫进来，而网关的 classpath 上没有 Servlet API，
 * 启动会直接失败。
 *
 * @author freedom0213
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
