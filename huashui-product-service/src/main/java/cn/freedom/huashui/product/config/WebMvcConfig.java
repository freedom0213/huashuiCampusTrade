package cn.freedom.huashui.product.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 相关配置：把本地上传目录映射成静态资源。
 *
 * <p>上传的图片存在磁盘上，需要一个 HTTP 入口才能被浏览器访问。
 * 这里把 {@code /uploads/**} 映射到上传根目录，
 * 前端 {@code <img src="/uploads/2026/09/xxx.jpg">} 经网关转发即可加载。
 *
 * @author freedom0213
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FileStorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // toAbsolutePath + normalize 保证无论启动目录在哪，映射到的都是同一个真实目录
        String location = Paths.get(properties.getUploadDir())
                .toAbsolutePath()
                .normalize()
                .toUri()
                .toString();

        // ⚠️ 必须以 / 结尾，否则 Spring 不会把它当作「目录」，
        // 结果是所有静态资源都报 NoResourceFoundException——
        // 服务能起来、接口都正常，只有图片 404，很容易查偏方向
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler(properties.getUrlPrefix() + "/**")
                .addResourceLocations(location);
    }
}
