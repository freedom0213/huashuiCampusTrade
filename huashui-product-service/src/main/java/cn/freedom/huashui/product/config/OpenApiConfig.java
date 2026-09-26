package cn.freedom.huashui.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档配置（Knife4j / OpenAPI 3）。
 *
 * <p>访问地址：{@code http://127.0.0.1:6003/doc.html}
 *
 * @author freedom0213
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI huashuiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("华水校园闲置交易平台 - 商品服务")
                        .description("商品分类、商品发布与编辑、图片上传、列表与筛选、商品详情。"
                                + "所有接口统一返回 {code, message, data} 结构。")
                        .version("1.0.0")
                        .contact(new Contact().name("freedom0213")));
    }
}
