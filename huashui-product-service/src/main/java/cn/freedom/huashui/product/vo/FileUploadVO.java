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

    @Schema(description = "文件字节数")
    private Long size;
}
