package cn.freedom.huashui.product.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。
 *
 * @author freedom0213
 */
@Data
@ConfigurationProperties(prefix = "huashui.file")
public class FileStorageProperties {

    /**
     * 本地存储根目录。相对路径以应用启动目录为基准。
     */
    private String uploadDir = "./uploads";

    /**
     * 对外访问前缀。存储层只负责把文件落盘并返回「带前缀的相对路径」，
     * 具体由谁来提供 HTTP 服务（本服务静态资源映射 / Nginx / OSS）
     * 由部署方式决定，业务代码不关心。
     */
    private String urlPrefix = "/uploads";

    /**
     * 允许上传的扩展名。白名单而不是黑名单：
     * 黑名单永远列不全，白名单漏掉的只是「本来也不该支持」的格式。
     */
    private String[] allowedExtensions = {"jpg", "jpeg", "png", "gif", "webp"};

    /** 单文件大小上限（字节），默认 5MB */
    private long maxSize = 5 * 1024 * 1024L;
}
