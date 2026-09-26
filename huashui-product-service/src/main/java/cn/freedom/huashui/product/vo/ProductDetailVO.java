package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情。
 *
 * <p>在列表卡片的基础上补充：多图、描述、分类名、驳回原因等。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "商品详情")
public class ProductDetailVO {

    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String coverUrl;

    @Schema(description = "图片地址列表，按 sort 升序，第一张是封面")
    private List<String> imageUrls;

    private Long categoryId;

    private String categoryName;

    private String campus;

    private String tradePlace;

    private Integer conditionLevel;

    private String conditionDesc;

    private Integer status;

    private String statusDesc;

    @Schema(description = "审核驳回原因，仅在状态为已驳回时非空")
    private String rejectReason;

    private Integer viewCount;

    private Integer favoriteCount;

    private LocalDateTime publishTime;

    private LocalDateTime soldTime;

    @Schema(description = "卖家 id。卖家昵称/头像由前端调用 /api/user/detail/{sellerId} 获取")
    private Long sellerId;

    @Schema(description = "是否为当前登录用户自己发布的商品")
    private Boolean owned;

    private LocalDateTime createTime;
}
