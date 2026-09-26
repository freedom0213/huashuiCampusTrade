package cn.freedom.huashui.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 商品服务启动类。
 *
 * <p>端口 6003，通过网关（6001）对外提供服务。
 * 网关路由：{@code /api/product/**}、{@code /api/category/**} → 本服务。
 *
 * @author freedom0213
 */
@SpringBootApplication
@EnableDiscoveryClient
@ConfigurationPropertiesScan
@MapperScan("cn.freedom.huashui.product.mapper")
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
