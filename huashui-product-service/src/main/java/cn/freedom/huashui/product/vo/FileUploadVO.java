package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传结果。
 *
 * @author freedom0213
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片上传结果")
public class FileUploadVO {

    @Schema(description = "可直接用于 img src 的访问路径，如 /uploads/2026/09/xxx.jpg")
    private String url;

    @Schema(description = "原始文件名")
    private String originalName;

    /**
     * 文件字节数。
     *
     * <p><b>用 {@code Integer} 而非 {@code Long}：</b>common 把 Long 全局序列化成了字符串
     * （为了让雪花 ID 在前端不丢精度），而字节数是「数量」、必须保持数字，
     * 否则前端算「已用多少 KB」时会得到字符串拼接的结果。
     * <p>图片上限只有 5MB（远小于 int 上限 2GB），用 Integer 完全够。
     * <p>约定：<b>Long 只用于 ID，数量类字段一律用 int / Integer。</b>
     */
    @Schema(description = "文件字节数（数字）")
    private Integer size;
}
