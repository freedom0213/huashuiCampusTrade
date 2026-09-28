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

    /**
     * 审核驳回原因。**只在「我的发布」里透出**，其余场景恒为 null。
     *
     * <p>为什么不做成「有值就返回」：驳回原因属于卖家私事。
     * 收藏列表用的是同一个 {@code ProductListVO}——商品被驳回后，
     * 当初收藏过它的用户仍会在收藏页看到这件商品；若无条件透出，
     * 买家就能读到「卖家为什么被驳回」。公共列表同理（虽然它只查在售、天然拿不到）。
     * 所以由服务层显式决定是否填充，而不是让「字段有值」自动等于「可以给外面看」。
     */
    @Schema(description = "审核驳回原因。仅「我的发布」返回，其余场景为 null")
    private String rejectReason;

    @Schema(description = "卖家 id。详情页跳转卖家主页 / 管理端定位卖家都用它")
    private Long sellerId;

    private Integer viewCount;

    private Integer favoriteCount;

    private LocalDateTime publishTime;

    /**
     * 提交时间（= 记录创建时间）。**仅管理端审核列表返回**，其余场景恒为 null。
     * <p>publishTime 在审核通过时才补记，待审核 / 已驳回商品拿不到它，
     * 管理端「提交时间」列需要的是管理员视角的「什么时候提交的」。
     */
    @Schema(description = "提交时间。仅管理端审核列表返回，其余场景为 null")
    private LocalDateTime createdAt;
}
