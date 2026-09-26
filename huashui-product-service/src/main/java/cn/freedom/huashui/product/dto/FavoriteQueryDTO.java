package cn.freedom.huashui.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 我的收藏查询参数。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "我的收藏查询参数")
public class FavoriteQueryDTO {

    @Min(value = 1, message = "页码最小为 1")
    @Schema(description = "页码，从 1 开始", defaultValue = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = 100, message = "每页条数最大为 100")
    @Schema(description = "每页条数", defaultValue = "10")
    private Integer size = 10;
}
