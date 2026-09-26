package cn.freedom.huashui.common.config;

import cn.freedom.huashui.common.web.GlobalExceptionHandler;
import cn.freedom.huashui.common.web.UserContextInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Servlet 服务的公共 Web 装配：全局异常处理 + 用户上下文拦截器。
 *
 * <p><b>关键约束</b>：加了 {@link ConditionalOnWebApplication} 且限定 SERVLET，
 * 因为网关是 WebFlux（Reactive）应用，一旦把它下面的 Servlet 配置装进网关，
 * 网关会因为同时存在两种 Web 栈而启动失败。
 *
 * @author freedom0213
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WebAutoConfiguration {

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 以匿名实现的方式注册 WebMvcConfigurer。
     * 这样自动配置类本身不必实现 WebMvcConfigurer 接口，职责更单一。
     */
    @Bean
    public WebMvcConfigurer userContextWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new UserContextInterceptor())
                        .addPathPatterns("/**");
            }
        };
    }
}
