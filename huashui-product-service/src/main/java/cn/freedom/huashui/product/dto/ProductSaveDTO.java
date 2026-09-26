package cn.freedom.huashui.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品发布 / 编辑请求。
 *
 * <p>发布与编辑字段完全一致，因此共用同一个 DTO，不为了「一个接口一个 DTO」而复制两份。
 * 两者的差别只在服务层的状态处理：发布后进入「待审核」，编辑后重新回到「待审核」。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "商品发布 / 编辑请求")
public class ProductSaveDTO {

    @NotNull(message = "请选择商品分类")
    @Schema(description = "分类 id")
    private Long categoryId;

    @NotBlank(message = "商品标题不能为空")
    @Size(max = 64, message = "商品标题最长 64 个字符")
    @Schema(description = "商品标题", example = "二手《数据结构》教材，几乎全新")
    private String title;

    @NotBlank(message = "商品描述不能为空")
    @Size(max = 1000, message = "商品描述最长 1000 个字符")
    @Schema(description = "商品描述，换行会被保留")
    private String description;

    @NotNull(message = "请填写售价")
    @DecimalMin(value = "0.01", message = "售价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "售价最多 8 位整数、2 位小数")
    @Schema(description = "售价", example = "25.00")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "原价必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "原价最多 8 位整数、2 位小数")
    @Schema(description = "原价，选填。用于详情页展示划线价", example = "45.00")
    private BigDecimal originalPrice;

    @NotBlank(message = "请选择校区")
    @Size(max = 16, message = "校区名称过长")
    @Schema(description = "校区，取值：龙子湖 / 花园 / 江淮", example = "龙子湖")
    private String campus;

    @NotBlank(message = "请填写交易地点")
    @Size(max = 128, message = "交易地点最长 128 个字符")
    @Schema(description = "交易地点展示文本，由前端按「校区 · 地标」拼接", example = "龙子湖 · 第二食堂")
    private String tradePlace;

    @NotNull(message = "请选择成色")
    @Min(value = 0, message = "成色取值不合法")
    @Max(value = 3, message = "成色取值不合法")
    @Schema(description = "成色：0 全新，1 九成新，2 七成新，3 五成新及以下", example = "1")
    private Integer conditionLevel;

    @NotEmpty(message = "请至少上传一张商品图片")
    @Size(max = 9, message = "最多上传 9 张图片")
    @Schema(description = "图片地址列表，第一张作为封面")
    private List<String> imageUrls;
}
