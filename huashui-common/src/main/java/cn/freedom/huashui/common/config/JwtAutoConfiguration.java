package cn.freedom.huashui.common.config;

import cn.freedom.huashui.common.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * JwtUtil 自动装配。
 *
 * <p>为什么用自动配置而不是让各服务写 {@code @Bean}：
 * 网关与 user-service 都需要签发 / 校验 token，把装配逻辑放在 common 里只写一次。
 *
 * <p>为什么加 {@link ConditionalOnProperty}：
 * product-service、order-service 不需要 JWT，它们不会配置 {@code huashui.jwt.secret}，
 * 加上这个条件后它们不会创建 JwtUtil，也不会因为缺配置而启动失败。
 *
 * <p>为什么用 SPI（AutoConfiguration.imports）而不是组件扫描：
 * 扫描要求各服务把 scanBasePackages 扩大到 common 包，会连带把 Servlet 专用的
 * Web 配置也扫进 WebFlux 网关，导致启动失败。SPI 由 Spring Boot 精确控制加载条件。
 *
 * @author freedom0213
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "huashui.jwt", name = "secret")
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtUtil jwtUtil(@Value("${huashui.jwt.secret}") String secret,
                           @Value("${huashui.jwt.ttl-minutes:120}") long ttlMinutes) {
        return new JwtUtil(secret, ttlMinutes);
    }
}
