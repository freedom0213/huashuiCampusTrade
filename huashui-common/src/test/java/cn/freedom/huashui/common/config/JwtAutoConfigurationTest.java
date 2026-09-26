package cn.freedom.huashui.common.config;

import cn.freedom.huashui.common.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 SPI 自动配置的生效条件。
 *
 * <p>这类测试的价值在于：自动配置的「不生效」通常不会抛异常，
 * 只会表现为「Bean 没被创建」，导致问题推迟到服务启动时才暴露。
 *
 * @author freedom0213
 */
class JwtAutoConfigurationTest {

    private static final String SECRET = "huashui-campus-trade-jwt-secret-key-2026";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtAutoConfiguration.class));

    @Test
    @DisplayName("配置了 huashui.jwt.secret 时自动创建 JwtUtil Bean")
    void shouldCreateJwtUtilWhenSecretConfigured() {
        runner.withPropertyValues("huashui.jwt.secret=" + SECRET)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtUtil.class);
                    assertThat(context.getBean(JwtUtil.class).getTtlMinutes()).isEqualTo(120L);
                });
    }

    @Test
    @DisplayName("未配置密钥时不创建 Bean，保证不需要 JWT 的服务能正常启动")
    void shouldNotCreateJwtUtilWhenSecretMissing() {
        runner.run(context -> assertThat(context).doesNotHaveBean(JwtUtil.class));
    }

    @Test
    @DisplayName("可通过 huashui.jwt.ttl-minutes 覆盖默认有效期")
    void shouldRespectCustomTtl() {
        runner.withPropertyValues("huashui.jwt.secret=" + SECRET,
                        "huashui.jwt.ttl-minutes=30")
                .run(context -> assertThat(context.getBean(JwtUtil.class).getTtlMinutes()).isEqualTo(30L));
    }
}
