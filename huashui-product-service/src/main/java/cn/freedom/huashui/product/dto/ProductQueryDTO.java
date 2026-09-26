package cn.freedom.huashui.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 商品列表查询参数。
 *
 * <p>所有条件都可自由组合：分类、关键词、校区、成色、卖家、排序。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "商品列表查询参数")
public class ProductQueryDTO {

    @Min(value = 1, message = "页码从 1 开始")
    @Schema(description = "页码，从 1 开始", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数至少为 1")
    @Max(value = 50, message = "每页最多 50 条")
    @Schema(description = "每页条数，最大 50", example = "20")
    private Integer size = 20;

    @Schema(description = "分类筛选")
    private Long categoryId;

    @Schema(description = "关键词，匹配商品标题")
    private String kw;

    @Schema(description = "校区筛选：龙子湖 / 花园 / 江淮")
    private String campus;

    @Schema(description = "成色筛选，含义是「不低于该成色」。传 1 表示九成新及以上（condition_level <= 1）", example = "1")
    private Integer conditionLevel;

    @Schema(description = "按卖家筛选，用于卖家主页")
    private Long sellerId;

    @Schema(description = "状态筛选。**仅在「我的发布」接口生效**；公开列表一律只看在售，忽略该参数")
    private Integer status;

    /**
     * 排序方式。取值为白名单枚举名，非法值由服务层兜底成 newest，
     * 绝不用字符串拼接进 SQL。
     */
    @Schema(description = "排序：newest（默认）/ price_asc / price_desc / views")
    private String sort = "newest";
}
