package cn.freedom.huashui.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权相关配置。
 *
 * <p>白名单放在配置文件而不是写死在 Java 里：新增一个免登录接口时不需要改代码、
 * 不需要重新打包，改配置重启即可。
 *
 * @author freedom0213
 */
@Data
@ConfigurationProperties(prefix = "huashui.gateway")
public class GatewayAuthProperties {

    /**
     * 免登录白名单，支持 Ant 风格通配符（如 {@code /api/product/detail/**}）。
     */
    private List<String> whiteList = new ArrayList<>();
}
