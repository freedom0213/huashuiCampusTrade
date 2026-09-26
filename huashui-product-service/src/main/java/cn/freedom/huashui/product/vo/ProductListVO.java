package cn.freedom.huashui.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表卡片。
 *
 * <p>字段严格对应前端设计 V1 §4.1 的卡片构成：
 * 封面图 + 交易地点胶囊 + 标题 + 价格 + 原价。
 * <b>刻意不含卖家昵称与头像</b>——对「要不要点进去」这个决策没有帮助，
 * 反而让卡片信息层级变乱。
 *
 * @author freedom0213
 */
@Data
@Schema(description = "商品列表卡片")
public class ProductListVO {

    private Long id;

    private String title;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String coverUrl;

    @Schema(description = "交易地点展示文本")
    private String tradePlace;

    @Schema(description = "校区")
    private String campus;

    @Schema(description = "成色 code：0 全新 / 1 九成新 / 2 七成新 / 3 五成新及以下")
    private Integer conditionLevel;

    @Schema(description = "成色中文，前端不用自己维护映射表")
    private String conditionDesc;

    @Schema(description = "状态 code")
    private Integer status;

    @Schema(description = "状态中文")
    private String statusDesc;

    private Integer viewCount;

    private Integer favoriteCount;

    private LocalDateTime publishTime;
}
