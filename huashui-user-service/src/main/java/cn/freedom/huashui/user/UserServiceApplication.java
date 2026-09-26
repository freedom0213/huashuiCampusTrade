package cn.freedom.huashui.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 用户服务启动类。
 *
 * <p>端口 6002，通过网关（6001）对外提供服务。
 *
 * <p><b>组件扫描范围保持在 {@code cn.freedom.huashui.user}</b>：
 * huashui-common 的 Bean 全部由 SPI 自动配置加载（Result、全局异常、拦截器、JwtUtil）。
 * 不要为了「扫到 common」而扩大扫描范围。
 *
 * @author freedom0213
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("cn.freedom.huashui.user.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
