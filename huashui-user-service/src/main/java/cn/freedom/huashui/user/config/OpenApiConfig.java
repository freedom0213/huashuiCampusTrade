package cn.freedom.huashui.user.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 *
 * <p>访问地址：{@code http://127.0.0.1:6002/doc.html}
 * 通过网关访问则是 {@code http://127.0.0.1:6001/api/user/doc.html}（需要放开白名单才能匿名打开）。
 *
 * @author freedom0213
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI huashuiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("华水校园闲置交易平台 - 用户服务")
                        .description("注册、登录、用户信息管理。所有接口统一返回 {code, message, data} 结构。")
                        .version("1.0.0")
                        .contact(new Contact().name("freedom0213")));
    }
}
