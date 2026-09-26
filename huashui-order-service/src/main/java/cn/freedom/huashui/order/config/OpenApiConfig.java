package cn.freedom.huashui.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 *
 * <p>访问地址：{@code http://127.0.0.1:6004/doc.html}
 *
 * @author freedom0213
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI huashuiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("华水校园闲置交易平台 - 交易服务")
                        .description("下单、订单状态机（待支付 → 已付款 → 交易完成，分支已取消）、"
                                + "我的订单、历史成交笔数。所有接口统一返回 {code, message, data} 结构。")
                        .version("1.0.0")
                        .contact(new Contact().name("freedom0213")));
    }
}
